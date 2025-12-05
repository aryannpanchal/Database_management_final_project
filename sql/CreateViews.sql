-- DROP existing views if they exist so the file can be rerun cleanly
DROP VIEW IF EXISTS ToppingPopularity;
DROP VIEW IF EXISTS ProfitByPizza;
DROP VIEW IF EXISTS ProfitByOrderType;

------------------------------------------------------------
-- 1) ToppingPopularity
--    Columns:
--      Topping       - topping name
--      ToppingCount  - how many times that topping has been used on pizzas
------------------------------------------------------------

CREATE VIEW ToppingPopularity AS
SELECT
    t.topping_TopName AS Topping,
    COUNT(pt.pizza_PizzaID) AS ToppingCount
FROM topping t
LEFT JOIN pizza_topping pt
    ON t.topping_TopID = pt.topping_TopID
GROUP BY t.topping_TopID, t.topping_TopName
ORDER BY ToppingCount DESC, Topping ASC;

------------------------------------------------------------
-- 2) ProfitByPizza
--    Columns:
--      Size       - pizza_Size
--      Crust      - pizza_CrustType
--      Profit     - total (cust - bus) for that size/crust
--      OrderMonth - month name (e.g., 'January')
------------------------------------------------------------

CREATE VIEW ProfitByPizza AS
SELECT
    p.pizza_Size AS Size,
    p.pizza_CrustType AS Crust,
    SUM(p.pizza_CustPrice - p.pizza_BusPrice) AS Profit,
    CASE
        WHEN MONTH(o.ordertable_OrderDateTime) = 1 THEN 'January'
        WHEN MONTH(o.ordertable_OrderDateTime) = 2 THEN 'February'
        WHEN MONTH(o.ordertable_OrderDateTime) = 3 THEN 'March'
        WHEN MONTH(o.ordertable_OrderDateTime) = 4 THEN 'April'
        WHEN MONTH(o.ordertable_OrderDateTime) = 5 THEN 'May'
        WHEN MONTH(o.ordertable_OrderDateTime) = 6 THEN 'June'
        WHEN MONTH(o.ordertable_OrderDateTime) = 7 THEN 'July'
        WHEN MONTH(o.ordertable_OrderDateTime) = 8 THEN 'August'
        WHEN MONTH(o.ordertable_OrderDateTime) = 9 THEN 'September'
        WHEN MONTH(o.ordertable_OrderDateTime) = 10 THEN 'October'
        WHEN MONTH(o.ordertable_OrderDateTime) = 11 THEN 'November'
        WHEN MONTH(o.ordertable_OrderDateTime) = 12 THEN 'December'
    END AS OrderMonth
FROM pizza p
JOIN ordertable o
    ON p.ordertable_OrderID = o.ordertable_OrderID
GROUP BY
    Size,
    Crust,
    OrderMonth
ORDER BY
    OrderMonth ASC,
    Size ASC,
    Crust ASC;

------------------------------------------------------------
-- 3) ProfitByOrderType
--    Columns:
--      CustomerType     - 'Dine-in', 'Pick-up', 'Delivery', or 'Total'
--      OrderMonth       - month name
--      TotalOrderPrice  - sum of ordertable_CustPrice
--      TotalOrderCost   - sum of ordertable_BusPrice
--      Profit           - TotalOrderPrice - TotalOrderCost
------------------------------------------------------------

CREATE VIEW ProfitByOrderType AS
SELECT
    CASE
        WHEN ot.ordertable_OrderType = 'dinein'   THEN 'Dine-in'
        WHEN ot.ordertable_OrderType = 'pickup'   THEN 'Pick-up'
        WHEN ot.ordertable_OrderType = 'delivery' THEN 'Delivery'
    END AS CustomerType,
    CASE
        WHEN MONTH(ot.ordertable_OrderDateTime) = 1 THEN 'January'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 2 THEN 'February'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 3 THEN 'March'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 4 THEN 'April'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 5 THEN 'May'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 6 THEN 'June'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 7 THEN 'July'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 8 THEN 'August'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 9 THEN 'September'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 10 THEN 'October'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 11 THEN 'November'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 12 THEN 'December'
    END AS OrderMonth,
    SUM(ot.ordertable_CustPrice) AS TotalOrderPrice,
    SUM(ot.ordertable_BusPrice) AS TotalOrderCost,
    SUM(ot.ordertable_CustPrice - ot.ordertable_BusPrice) AS Profit
FROM ordertable ot
GROUP BY
    CustomerType,
    OrderMonth

UNION ALL

-- Total across all order types for each month
SELECT
    'Total' AS CustomerType,
    CASE
        WHEN MONTH(ot.ordertable_OrderDateTime) = 1 THEN 'January'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 2 THEN 'February'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 3 THEN 'March'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 4 THEN 'April'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 5 THEN 'May'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 6 THEN 'June'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 7 THEN 'July'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 8 THEN 'August'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 9 THEN 'September'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 10 THEN 'October'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 11 THEN 'November'
        WHEN MONTH(ot.ordertable_OrderDateTime) = 12 THEN 'December'
    END AS OrderMonth,
    SUM(ot.ordertable_CustPrice) AS TotalOrderPrice,
    SUM(ot.ordertable_BusPrice) AS TotalOrderCost,
    SUM(ot.ordertable_CustPrice - ot.ordertable_BusPrice) AS Profit
FROM ordertable ot
GROUP BY
    OrderMonth

ORDER BY
    CASE CustomerType
        WHEN 'Dine-in'  THEN 1
        WHEN 'Pick-up'  THEN 2
        WHEN 'Delivery' THEN 3
        WHEN 'Total'    THEN 4
    END,
    OrderMonth ASC;
