-- ==========================
-- CreateViews.sql (FINAL)
-- ==========================
USE PizzaDB;

-- ============================================================
-- 1. TOPPING POPULARITY
-- ============================================================

DROP VIEW IF EXISTS ToppingPopularity;

CREATE VIEW ToppingPopularity AS
SELECT
    t.topping_TopName AS Topping,
    COALESCE(
        SUM(
            CASE
                WHEN pt.pizza_topping_IsDouble = 1 THEN 2
                ELSE 1
            END
        ), 0
    ) AS ToppingCount
FROM topping t
LEFT JOIN pizza_topping pt
    ON t.topping_TopID = pt.topping_TopID
GROUP BY t.topping_TopName
ORDER BY ToppingCount DESC, Topping ASC;

-- ============================================================
-- 2. PROFIT BY PIZZA
-- ============================================================

DROP VIEW IF EXISTS ProfitByPizza;

CREATE VIEW ProfitByPizza AS
SELECT
    CONCAT(UCASE(LEFT(p.pizza_Size,1)), SUBSTRING(p.pizza_Size,2)) AS Size,
    p.pizza_CrustType AS Crust,
    SUM(p.pizza_CustPrice - p.pizza_BusPrice) AS Profit,
    DATE_FORMAT(p.pizza_PizzaDate, '%m/%Y') AS OrderMonth
FROM pizza p
GROUP BY
    CONCAT(UCASE(LEFT(p.pizza_Size,1)), SUBSTRING(p.pizza_Size,2)),
    p.pizza_CrustType,
    DATE_FORMAT(p.pizza_PizzaDate, '%m/%Y')
ORDER BY Profit DESC, Size ASC, Crust ASC;

-- ============================================================
-- 3. PROFIT BY ORDER TYPE
-- ============================================================

DROP VIEW IF EXISTS ProfitByOrderType;

CREATE VIEW ProfitByOrderType AS
SELECT
    ot.ordertable_OrderType AS CustomerType,
    DATE_FORMAT(ot.ordertable_OrderDateTime, '%m/%Y') AS OrderMonth,
    SUM(ot.ordertable_CustPrice) AS TotalOrderPrice,
    SUM(ot.ordertable_BusPrice) AS TotalOrderCost,
    SUM(ot.ordertable_CustPrice - ot.ordertable_BusPrice) AS Profit
FROM ordertable ot
GROUP BY ot.ordertable_OrderType,
         DATE_FORMAT(ot.ordertable_OrderDateTime, '%m/%Y')

UNION ALL

SELECT
    'Total' AS CustomerType,
    '' AS OrderMonth,
    SUM(ot.ordertable_CustPrice),
    SUM(ot.ordertable_BusPrice),
    SUM(ot.ordertable_CustPrice - ot.ordertable_BusPrice)
FROM ordertable ot

ORDER BY
    CASE CustomerType
        WHEN 'dinein' THEN 1
        WHEN 'pickup' THEN 2
        WHEN 'delivery' THEN 3
        WHEN 'Total' THEN 4
    END,
    OrderMonth ASC;
