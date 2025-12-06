-- ============================================================
-- CreateViews.sql
-- Purpose: Define reporting views on top of PizzaDB tables.
-- ============================================================

USE PizzaDB;

-- ------------------------------------------------------------
-- VIEW 1: ToppingPopularity
-- Goal:
--   Count how many times each topping is used across all pizzas.
--   Double toppings count as 2, regular as 1, none as 0.
--   Uses LEFT JOIN so toppings that have never been used still show up.
-- ------------------------------------------------------------
DROP VIEW IF EXISTS ToppingPopularity;

CREATE VIEW ToppingPopularity AS
SELECT
    -- Name of the topping
    t.topping_TopName AS Topping,

    -- Total count across all pizzas:
    --   double = 2, single = 1, other/null = 0
    COALESCE(SUM(
        CASE
            WHEN pt.pizza_topping_IsDouble = 1 THEN 2
            WHEN pt.pizza_topping_IsDouble = 0 THEN 1
            ELSE 0
        END
    ), 0) AS ToppingCount
FROM topping t
    -- LEFT JOIN ensures toppings not used in any pizza still appear
    LEFT JOIN pizza_topping pt ON pt.topping_TopID = t.topping_TopID
GROUP BY t.topping_TopName
-- Sort by popularity (desc), then alphabetically by topping name
ORDER BY ToppingCount DESC, Topping ASC;

-- ------------------------------------------------------------
-- VIEW 2: ProfitByPizza
-- Goal:
--   Compute profit per (size, crust) combination per month.
--   Profit = sum(customer price - business cost).
--   OrderMonth is displayed as M/YYYY (e.g. 2/2024).
-- ------------------------------------------------------------
DROP VIEW IF EXISTS ProfitByPizza;

CREATE VIEW ProfitByPizza AS
SELECT
    -- Pizza dimensions
    p.pizza_Size AS Size,
    p.pizza_CrustType AS Crust,

    -- Overall profit for that size+crust within the month
    ROUND(SUM(p.pizza_CustPrice - p.pizza_BusPrice), 2) AS Profit,

    -- Month label derived from earliest pizza date in group
    DATE_FORMAT(MIN(p.pizza_PizzaDate), '%c/%Y') AS OrderMonth
FROM pizza p
-- Group by (month, size, crust)
GROUP BY DATE_FORMAT(p.pizza_PizzaDate, '%Y-%m'), p.pizza_Size, p.pizza_CrustType
-- Sort by Profit ascending (losers first, big winners last)
ORDER BY Profit ASC;

-- ------------------------------------------------------------
-- VIEW 3: ProfitByOrderType
-- Goal:
--   Summarize revenue, cost, and profit per customer type (dinein,
--   pickup, delivery) per month, plus a grand total row.
--   Takes into account:
--     * order-level $ discounts
--     * order-level % discounts (compounded multiplicatively)
-- ------------------------------------------------------------
DROP VIEW IF EXISTS ProfitByOrderType;

CREATE VIEW ProfitByOrderType AS
WITH order_totals AS (
  SELECT 
    -- Basic order info
    o.ordertable_OrderID AS OrderID,
    LOWER(o.ordertable_OrderType) AS CustomerType,
    DATE_FORMAT(o.ordertable_OrderDateTime, '%c/%Y') AS OrderMonth,

    -- ------------------------------------------------------
    -- base_price: sum of all pizza_CustPrice for the order
    -- ------------------------------------------------------
    (SELECT COALESCE(SUM(p.pizza_CustPrice),0)
       FROM pizza p
      WHERE p.ordertable_OrderID = o.ordertable_OrderID) AS base_price,

    -- ------------------------------------------------------
    -- base_cost: sum of all pizza_BusPrice (internal cost)
    -- ------------------------------------------------------
    (SELECT COALESCE(SUM(p.pizza_BusPrice),0)
       FROM pizza p
      WHERE p.ordertable_OrderID = o.ordertable_OrderID) AS base_cost,

    -- ------------------------------------------------------
    -- dollar_off: sum of all flat $ discounts for this order
    -- ------------------------------------------------------
    (SELECT COALESCE(SUM(d.discount_Amount),0)
       FROM order_discount od
       JOIN discount d ON d.discount_DiscountID = od.discount_DiscountID
      WHERE od.ordertable_OrderID = o.ordertable_OrderID
        AND d.discount_IsPercent = 0) AS dollar_off,

    -- ------------------------------------------------------
    -- percent_factor:
    --   If you have multiple percentage discounts, you multiply
    --   (1 - d1%) * (1 - d2%) * ...
    --   ln(a*b) = ln(a) + ln(b), so we use exponent of sum of ln's:
    --   EXP(SUM( LN(1 - percentage) ))
    -- ------------------------------------------------------
    (SELECT EXP(COALESCE(SUM(LN(1 - (d.discount_Amount/100))),0))
       FROM order_discount od
       JOIN discount d ON d.discount_DiscountID = od.discount_DiscountID
      WHERE od.ordertable_OrderID = o.ordertable_OrderID
        AND d.discount_IsPercent = 1) AS percent_factor
  FROM ordertable o
)

-- ------------------------------------------------------------
-- Main query: per customerType + month
-- Computes:
--   TotalOrderPrice = total revenue after discounts, not below 0
--   TotalOrderCost  = total cost
--   Profit          = TotalOrderPrice - TotalOrderCost
-- ------------------------------------------------------------
SELECT 
  t.CustomerType,
  t.OrderMonth,

  -- Sum of discounted prices, truncated at 0 per order
  CAST(
    ROUND(
      SUM(
        GREATEST(
          (t.base_price - t.dollar_off) * t.percent_factor,
          0
        )
      ),
      2
    ) AS DECIMAL(10,2)
  ) AS TotalOrderPrice,

  -- Sum of base_cost
  CAST(
    ROUND(
      SUM(t.base_cost),
      2
    ) AS DECIMAL(10,2)
  ) AS TotalOrderCost,

  -- Profit = TotalOrderPrice - TotalOrderCost
  CAST(
    ROUND(
      SUM(
        GREATEST(
          (t.base_price - t.dollar_off) * t.percent_factor,
          0
        ) - t.base_cost
      ),
      2
    ) AS DECIMAL(10,2)
  ) AS Profit
FROM order_totals t
GROUP BY t.CustomerType, t.OrderMonth

UNION ALL

-- ------------------------------------------------------------
-- "Grand Total" row:
--   Collapses all customer types and months into a single total.
--   CustomerType is empty string, OrderMonth = 'Grand Total'.
-- ------------------------------------------------------------
SELECT 
  '' AS CustomerType,
  'Grand Total' AS OrderMonth,

  CAST(
    ROUND(
      SUM(
        GREATEST(
          (t.base_price - t.dollar_off) * t.percent_factor,
          0
        )
      ),
      2
    ) AS DECIMAL(10,2)
  ) AS TotalOrderPrice,

  CAST(
    ROUND(
      SUM(t.base_cost),
      2
    ) AS DECIMAL(10,2)
  ) AS TotalOrderCost,

  CAST(
    ROUND(
      SUM(
        GREATEST(
          (t.base_price - t.dollar_off) * t.percent_factor,
          0
        ) - t.base_cost
      ),
      2
    ) AS DECIMAL(10,2)
  ) AS Profit
FROM order_totals t

-- Ensure the grand total row appears last
ORDER BY 
  CASE WHEN CustomerType = '' THEN 1 ELSE 0 END,
  Profit ASC;
