-- CreateViews.sql
-- Views used by the reports in DBNinja

USE pizzadb;

-- Drop old versions if they exist
DROP VIEW IF EXISTS ToppingPopularity;
DROP VIEW IF EXISTS ProfitByPizza;
DROP VIEW IF EXISTS ProfitByOrderType;

------------------------------------------------------------
-- 1) ToppingPopularity
--    Count how many *pizzas* each topping appears on.
--    (Extra / double still counts as 1 use on that pizza.)
------------------------------------------------------------
CREATE VIEW ToppingPopularity AS
SELECT
    t.topping_TopName AS Topping,
    COALESCE(COUNT(pt.pizza_PizzaID), 0) AS ToppingCount
FROM topping t
LEFT JOIN pizza_topping pt
       ON t.topping_TopID = pt.topping_TopID
GROUP BY
    t.topping_TopName
ORDER BY
    ToppingCount DESC,
    Topping ASC;

------------------------------------------------------------
-- 2) ProfitByPizza
--    One row per (Size, Crust):
--      Profit = SUM(customer price - business price) for ALL pizzas
--      LastOrderDate = month/year of the most recent pizza of that type
--    The Java code will print headers:
--      "Pizza Size", "Pizza Crust", "Profit", "Last Order Date"
------------------------------------------------------------
CREATE VIEW ProfitByPizza AS
SELECT
    p.pizza_Size       AS Size,
    p.pizza_CrustType  AS Crust,
    ROUND(SUM(p.pizza_CustPrice - p.pizza_BusPrice), 2) AS Profit,
    DATE_FORMAT(MAX(p.pizza_PizzaDate), '%c/%Y')        AS LastOrderDate
FROM pizza p
GROUP BY
    p.pizza_Size,
    p.pizza_CrustType
ORDER BY
    Profit ASC,
    Size ASC,
    Crust ASC;

------------------------------------------------------------
-- 3) ProfitByOrderType
--    One row per (OrderType, Month) + a Grand Total row.
--    Month formatted as M/YYYY (no leading zero).
--    Java prints headings exactly as instructor specified.
------------------------------------------------------------
CREATE VIEW ProfitByOrderType AS
SELECT
    ot.ordertable_OrderType                                AS CustomerType,
    DATE_FORMAT(ot.ordertable_OrderDateTime, '%c/%Y')      AS OrderMonth,
    ROUND(SUM(ot.ordertable_CustPrice), 2)                 AS TotalOrderPrice,
    ROUND(SUM(ot.ordertable_BusPrice), 2)                  AS TotalOrderCost,
    ROUND(SUM(ot.ordertable_CustPrice - ot.ordertable_BusPrice), 2) AS Profit
FROM ordertable ot
GROUP BY
    ot.ordertable_OrderType,
    DATE_FORMAT(ot.ordertable_OrderDateTime, '%c/%Y')

UNION ALL

SELECT
    ''                         AS CustomerType,   -- prints as blank
    'Grand Total'              AS OrderMonth,
    ROUND(SUM(ot.ordertable_CustPrice), 2)        AS TotalOrderPrice,
    ROUND(SUM(ot.ordertable_BusPrice), 2)         AS TotalOrderCost,
    ROUND(SUM(ot.ordertable_CustPrice - ot.ordertable_BusPrice), 2) AS Profit
FROM ordertable ot

ORDER BY
    CASE
        WHEN CustomerType = 'dinein'   THEN 1
        WHEN CustomerType = 'pickup'   THEN 2
        WHEN CustomerType = 'delivery' THEN 3
        ELSE 4  -- Grand Total last
    END,
    CASE
        WHEN OrderMonth = 'Grand Total'
            THEN STR_TO_DATE('9999-12-31', '%Y-%m-%d')
        ELSE STR_TO_DATE(CONCAT('01/', OrderMonth), '%d/%c/%Y')
    END;
