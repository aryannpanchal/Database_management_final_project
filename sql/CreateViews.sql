-- ============================================================
-- CreateViews.sql
-- Purpose: Define reporting views on top of PizzaDB tables.
-- ============================================================

USE `PizzaDB`;

-- ------------------------------------------------------------
-- VIEW 1: ToppingPopularity
-- Goal:
--   Count how many times each topping is used across all pizzas.
--   Double toppings count as 2, regular as 1, none as 0.
--   Uses LEFT JOIN so toppings that have never been used still show up.
-- ------------------------------------------------------------
DROP VIEW IF EXISTS `ToppingPopularity`;

CREATE VIEW `ToppingPopularity` AS
SELECT
    t.topping_TopName AS `Topping`,
    COALESCE(
        SUM(
            CASE
                WHEN pt.pizza_topping_IsDouble = 1 THEN 2
                WHEN pt.pizza_topping_IsDouble = 0 THEN 1
                ELSE 0
            END
        ),
        0
    ) AS `ToppingCount`
FROM `topping` AS t
LEFT JOIN `pizza_topping` AS pt
    ON pt.topping_TopID = t.topping_TopID
GROUP BY
    t.topping_TopName
ORDER BY
    `ToppingCount` DESC,
    `Topping` ASC;

-- ------------------------------------------------------------
-- VIEW 2: ProfitByPizza
-- Goal:
--   Compute profit per (size, crust) combination per month.
--   Profit = sum(customer price - business cost).
--   OrderMonth is displayed as M/YYYY (e.g. 2/2024).
-- ------------------------------------------------------------
DROP VIEW IF EXISTS `ProfitByPizza`;

CREATE VIEW `ProfitByPizza` AS
SELECT
    p.pizza_Size AS `Size`,
    p.pizza_CrustType AS `Crust`,
    ROUND(
        SUM(p.pizza_CustPrice - p.pizza_BusPrice),
        2
    ) AS `Profit`,
    DATE_FORMAT(
        MIN(p.pizza_PizzaDate),
        '%c/%Y'
    ) AS `OrderMonth`
FROM `pizza` AS p
GROUP BY
    DATE_FORMAT(p.pizza_PizzaDate, '%Y-%m'),
    p.pizza_Size,
    p.pizza_CrustType
ORDER BY
    `Profit` ASC;

-- ------------------------------------------------------------
-- VIEW 3: ProfitByOrderType
-- Goal:
--   Summarize revenue, cost, and profit per customer type (dinein,
--   pickup, delivery) per month, plus a grand total row.
--   Takes into account:
--     * order-level $ discounts
--     * order-level % discounts (compounded multiplicatively)
-- ------------------------------------------------------------
DROP VIEW IF EXISTS `ProfitByOrderType`;

CREATE VIEW `ProfitByOrderType` AS
WITH `order_totals` AS (
    SELECT
        o.ordertable_OrderID AS `OrderID`,
        LOWER(o.ordertable_OrderType) AS `CustomerType`,
        DATE_FORMAT(
            o.ordertable_OrderDateTime,
            '%c/%Y'
        ) AS `OrderMonth`,
        (
            SELECT COALESCE(SUM(p.pizza_CustPrice), 0)
            FROM `pizza` AS p
            WHERE p.ordertable_OrderID = o.ordertable_OrderID
        ) AS `base_price`,
        (
            SELECT COALESCE(SUM(p.pizza_BusPrice), 0)
            FROM `pizza` AS p
            WHERE p.ordertable_OrderID = o.ordertable_OrderID
        ) AS `base_cost`,
        (
            SELECT COALESCE(SUM(d.discount_Amount), 0)
            FROM `order_discount` AS od
            JOIN `discount` AS d
                ON d.discount_DiscountID = od.discount_DiscountID
            WHERE od.ordertable_OrderID = o.ordertable_OrderID
              AND d.discount_IsPercent = 0
        ) AS `dollar_off`,
        (
            SELECT EXP(
                COALESCE(
                    SUM(
                        LN(
                            1 - (d.discount_Amount / 100)
                        )
                    ),
                    0
                )
            )
            FROM `order_discount` AS od
            JOIN `discount` AS d
                ON d.discount_DiscountID = od.discount_DiscountID
            WHERE od.ordertable_OrderID = o.ordertable_OrderID
              AND d.discount_IsPercent = 1
        ) AS `percent_factor`
    FROM `ordertable` AS o
)

SELECT
    t.CustomerType,
    t.OrderMonth,
    CAST(
        ROUND(
            SUM(
                GREATEST(
                    (t.base_price - t.dollar_off) * t.percent_factor,
                    0
                )
            ),
            2
        ) AS DECIMAL(10, 2)
    ) AS `TotalOrderPrice`,
    CAST(
        ROUND(
            SUM(t.base_cost),
            2
        ) AS DECIMAL(10, 2)
    ) AS `TotalOrderCost`,
    CAST(
        ROUND(
            SUM(
                GREATEST(
                    (t.base_price - t.dollar_off) * t.percent_factor,
                    0
                ) - t.base_cost
            ),
            2
        ) AS DECIMAL(10, 2)
    ) AS `Profit`
FROM `order_totals` AS t
GROUP BY
    t.CustomerType,
    t.OrderMonth

UNION ALL

SELECT
    '' AS `CustomerType`,
    'Grand Total' AS `OrderMonth`,
    CAST(
        ROUND(
            SUM(
                GREATEST(
                    (t.base_price - t.dollar_off) * t.percent_factor,
                    0
                )
            ),
            2
        ) AS DECIMAL(10, 2)
    ) AS `TotalOrderPrice`,
    CAST(
        ROUND(
            SUM(t.base_cost),
            2
        ) AS DECIMAL(10, 2)
    ) AS `TotalOrderCost`,
    CAST(
        ROUND(
            SUM(
                GREATEST(
                    (t.base_price - t.dollar_off) * t.percent_factor,
                    0
                ) - t.base_cost
            ),
            2
        ) AS DECIMAL(10, 2)
    ) AS `Profit`
FROM `order_totals` AS t

ORDER BY
    CASE
        WHEN CustomerType = '' THEN 1
        ELSE 0
    END,
    `Profit` ASC;
