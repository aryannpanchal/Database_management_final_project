-- ==========================
-- CreateViews.sql (FINAL)
-- ==========================
-- Student Name: Aryan Panchal

USE PizzaDB;

-- --------------------------
-- ToppingPopularity
-- --------------------------

DROP VIEW IF EXISTS ToppingPopularity;

CREATE VIEW ToppingPopularity AS
SELECT
    t.topping_TopName AS Topping,
    COALESCE(
        SUM(
            CASE
                WHEN pt.pizza_topping_IsDouble = 1 THEN 2
                WHEN pt.pizza_topping_IsDouble = 0 THEN 1
                ELSE 0
            END
        ),
        0
    ) AS ToppingCount
FROM topping t
LEFT JOIN pizza_topping pt
    ON pt.topping_TopID = t.topping_TopID
GROUP BY t.topping_TopName
ORDER BY ToppingCount DESC, Topping ASC;

-- --------------------------
-- ProfitByPizza
-- --------------------------

DROP VIEW IF EXISTS ProfitByPizza;

CREATE VIEW ProfitByPizza AS
SELECT
    CONCAT(UCASE(LEFT(p.pizza_Size,1)), SUBSTRING(p.pizza_Size,2)) AS Size,
    p.pizza_CrustType AS Crust,
    SUM(p.pizza_CustPrice - p.pizza_BusPrice) AS Profit,
    DATE_FORMAT(p.pizza_PizzaDate, '%c/%Y') AS OrderMonth
FROM pizza p
GROUP BY
    CONCAT(UCASE(LEFT(p.pizza_Size,1)), SUBSTRING(p.pizza_Size,2)),
    p.pizza_CrustType,
    DATE_FORMAT(p.pizza_PizzaDate, '%c/%Y')
ORDER BY Profit ASC;

-- --------------------------
-- ProfitByOrderType
-- --------------------------

DROP VIEW IF EXISTS ProfitByOrderType;

CREATE VIEW ProfitByOrderType AS
SELECT *
FROM (
    -- per-order-type, per-month rows
    SELECT
        CAST(ot.ordertable_OrderType AS CHAR(20)) AS CustomerType,
        CAST(DATE_FORMAT(ot.ordertable_OrderDateTime, '%c/%Y') AS CHAR(20)) AS OrderMonth,
        SUM(ot.ordertable_CustPrice) AS TotalOrderPrice,
        SUM(ot.ordertable_BusPrice) AS TotalOrderCost,
        SUM(ot.ordertable_CustPrice - ot.ordertable_BusPrice) AS Profit
    FROM ordertable ot
    GROUP BY ot.ordertable_OrderType,
             DATE_FORMAT(ot.ordertable_OrderDateTime, '%c/%Y')

    UNION ALL

    -- grand total row
    SELECT
        CAST('' AS CHAR(20)) AS CustomerType,
        CAST('Grand Total' AS CHAR(20)) AS OrderMonth,
        SUM(ot.ordertable_CustPrice) AS TotalOrderPrice,
        SUM(ot.ordertable_BusPrice) AS TotalOrderCost,
        SUM(ot.ordertable_CustPrice - ot.ordertable_BusPrice) AS Profit
    FROM ordertable ot
) rows_
ORDER BY
    CASE rows_.CustomerType
        WHEN 'dinein'  THEN 1
        WHEN 'delivery' THEN 2
        WHEN 'pickup'  THEN 3
        WHEN ''        THEN 4  -- grand total
        ELSE 5
    END,
    rows_.Profit ASC;
