-- ============================================================
-- PopulateData.sql
-- Purpose:
--   Seed PizzaDB with initial data (base prices, toppings,
--   discounts, customers, sample orders/pizzas/toppings).
-- ============================================================

USE PizzaDB;

-- ============================================================
-- Base Prices
-- One row per (size, crust type), with customer price
-- and internal business cost.
-- ============================================================
INSERT INTO baseprice (baseprice_Size, baseprice_CrustType, baseprice_CustPrice, baseprice_BusPrice) VALUES
('Small','Thin',         3.00, 0.50),
('Small','Original',     3.00, 0.75),
('Small','Pan',          3.50, 1.00),
('Small','Gluten-Free',  4.00, 2.00),
('Medium','Thin',        5.00, 1.00),
('Medium','Original',    5.00, 1.50),
('Medium','Pan',         6.00, 2.25),
('Medium','Gluten-Free', 6.25, 3.00),
('Large','Thin',         8.00, 1.25),
('Large','Original',     8.00, 2.00),
('Large','Pan',          9.00, 3.00),
('Large','Gluten-Free',  9.50, 4.00),
('XLarge','Thin',       10.00, 2.00),
('XLarge','Original',   10.00, 3.00),
('XLarge','Pan',        11.50, 4.50),
('XLarge','Gluten-Free',12.50, 6.00);

-- ============================================================
-- Toppings
-- Contains ingredient cost, customer price, and inventory
-- for each topping option.
-- ============================================================
INSERT INTO topping
  (topping_TopName, topping_SmallAMT, topping_MedAMT, topping_LgAMT, topping_XLAMT,
   topping_CustPrice, topping_BusPrice, topping_MinINVT, topping_CurINVT)
VALUES
('Pepperoni',         2.00, 2.75, 3.50, 4.50, 1.25, 0.20, 50, 100),
('Sausage',           2.50, 3.00, 3.50, 4.25, 1.25, 0.15, 50, 100),
('Ham',               2.00, 2.50, 3.25, 4.00, 1.50, 0.15, 25,  78),
('Chicken',           1.50, 2.00, 2.25, 3.00, 1.75, 0.25, 25,  56),
('Green Pepper',      1.00, 1.50, 2.00, 2.50, 0.50, 0.02, 25,  79),
('Onion',             1.00, 1.50, 2.00, 2.75, 0.50, 0.02, 25,  85),
('Roma Tomato',       2.00, 3.00, 3.50, 4.50, 0.75, 0.03, 10,  86),
('Mushrooms',         1.50, 2.00, 2.50, 3.00, 0.75, 0.10, 50,  52),
('Black Olives',      0.75, 1.00, 1.50, 2.00, 0.60, 0.10, 25,  39),
('Pineapple',         1.00, 1.25, 1.75, 2.00, 1.00, 0.25,  0,  15),
('Jalapenos',         0.50, 0.75, 1.25, 1.75, 0.50, 0.05,  0,  64),
('Banana Peppers',    0.60, 1.00, 1.30, 1.75, 0.50, 0.05,  0,  36),
('Regular Cheese',    2.00, 3.50, 5.00, 7.00, 0.50, 0.12, 50, 250),
('Four Cheese Blend', 2.00, 3.50, 5.00, 7.00, 1.00, 0.15, 25, 150),
('Feta Cheese',       1.75, 3.00, 4.00, 5.50, 1.50, 0.18,  0,  75),
('Goat Cheese',       1.60, 2.75, 4.00, 5.50, 1.50, 0.20,  0,  54),
('Bacon',             1.00, 1.50, 2.00, 3.00, 1.50, 0.25,  0,  89);

-- ============================================================
-- Temporary staging table for pizza toppings
-- This is used while building sample orders/pizzas and then
-- flushed into pizza_topping at the end.
-- ============================================================
DROP TEMPORARY TABLE IF EXISTS TempPizzaToppings;

CREATE TEMPORARY TABLE TempPizzaToppings (
    -- Pizza ID that the topping applies to
    pizza_PizzaID INT NOT NULL,

    -- Topping ID being applied
    topping_TopID INT NOT NULL,

    -- 0/1 flag for single or double topping
    pizza_topping_IsDouble INT NOT NULL
);

-- ============================================================
-- Discounts
-- Both dollar-based and percentage-based discounts.
-- ============================================================
INSERT INTO discount (discount_DiscountName, discount_Amount, discount_IsPercent) VALUES
('Employee',             15.00, 1),
('Lunch Special Medium',  1.00, 0),
('Lunch Special Large',   2.00, 0),
('Specialty Pizza',       1.50, 0),
('Happy Hour',           10.00, 1),
('Gameday Special',      20.00, 1);

-- ============================================================
-- Customers
-- Basic sample customer data with phone numbers.
-- ============================================================
INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum) VALUES
('Andrew','Wilkes-Krier','8642545861'),
('Matt','Engers','8644749953'),
('Frank','Turner','8642328944'),
('Milo','Auckerman','8648785679');

-- ============================================================
-- ORDERS
-- Each order section:
--   1) Insert into ordertable
--   2) Insert into dinein/pickup/delivery
--   3) Insert pizzas and toppings
--   4) Add discounts (order or pizza level)
--   5) Recompute order totals using a consistent formula
-- ============================================================

-- ------------------------------------------------------------
-- ORDER #1 : Dine-in order with a single large thin pizza
-- Date: current year-01-05 12:03:00
-- ------------------------------------------------------------
INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime,
                        ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete)
VALUES (NULL, 'dinein', CONCAT(YEAR(CURDATE()),'-01-05 12:03:00'), 0.00, 0.00, 1);
SET @o1 := LAST_INSERT_ID();

-- Dine-in details: table 21
INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES
(@o1, 21);

-- Single large thin pizza on order #1
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('Large','Thin', @o1, 'completed', CONCAT(YEAR(CURDATE()),'-01-05 12:03:00'), 19.75, 3.68);

-- Regular Cheese (double)
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 1
FROM pizza p
JOIN topping t ON t.topping_TopName='Regular Cheese'
WHERE p.ordertable_OrderID=@o1;

-- Pepperoni (single)
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Pepperoni'
WHERE p.ordertable_OrderID=@o1;

-- Sausage (single)
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Sausage'
WHERE p.ordertable_OrderID=@o1;

-- Pizza-level discount: "Lunch Special Large"
INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
SELECT p.pizza_PizzaID, d.discount_DiscountID
FROM pizza p, discount d
WHERE p.ordertable_OrderID=@o1 AND d.discount_DiscountName='Lunch Special Large';

-- Recompute totals for order #1 (taking discounts into account)
UPDATE ordertable o
SET o.ordertable_CustPrice = ROUND(
        (
            -- Total pizza customer price for the order
            COALESCE((
                SELECT SUM(p.pizza_CustPrice)
                FROM pizza p
                WHERE p.ordertable_OrderID=o.ordertable_OrderID
            ), 0)
            --
            -- Minus any flat ($) order-level discounts
            --
            - COALESCE((
                SELECT SUM(d.discount_Amount)
                FROM order_discount od
                JOIN discount d USING(discount_DiscountID)
                WHERE od.ordertable_OrderID=o.ordertable_OrderID
                  AND d.discount_IsPercent=0
            ), 0)
        )
        *
        -- Apply any percentage-based order-level discounts multiplicatively
        EXP(COALESCE((
            SELECT SUM(LN(1 - (d.discount_Amount/100)))
            FROM order_discount od
            JOIN discount d USING(discount_DiscountID)
            WHERE od.ordertable_OrderID=o.ordertable_OrderID
              AND d.discount_IsPercent=1
        ), 0)), 2),
    -- Total business cost is simply sum of pizza_BusPrice
    o.ordertable_BusPrice = ROUND(
        COALESCE((
            SELECT SUM(p.pizza_BusPrice)
            FROM pizza p
            WHERE p.ordertable_OrderID=o.ordertable_OrderID
        ), 0), 2)
WHERE o.ordertable_OrderID=@o1;

-- ------------------------------------------------------------
-- ORDER #2 : Dine-in, two pizzas (A and B) and discounts
-- Date: current year-02-03 12:05:00
-- ------------------------------------------------------------
INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime,
                        ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete)
VALUES (NULL, 'dinein', CONCAT(YEAR(CURDATE()),'-02-03 12:05:00'), 0.00, 0.00, 1);
SET @o2 := LAST_INSERT_ID();

-- Dine-in at table 4
INSERT INTO dinein (ordertable_OrderID, dinein_TableNum) VALUES
(@o2, 4);

-- ----------------------
-- Pizza A (Medium Pan)
-- ----------------------
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('Medium','Pan', @o2, 'completed', CONCAT(YEAR(CURDATE()),'-02-03 12:05:00'), 13.85, 3.23);

-- Feta Cheese
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Feta Cheese'
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Medium' AND p.pizza_CrustType='Pan';

-- Black Olives
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Black Olives'
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Medium' AND p.pizza_CrustType='Pan';

-- Roma Tomato
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Roma Tomato'
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Medium' AND p.pizza_CrustType='Pan';

-- Mushrooms
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Mushrooms'
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Medium' AND p.pizza_CrustType='Pan';

-- Banana Peppers
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Banana Peppers'
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Medium' AND p.pizza_CrustType='Pan';

-- Specialty Pizza discount on this specific pizza
INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
SELECT p.pizza_PizzaID, d.discount_DiscountID
FROM pizza p, discount d
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Medium' AND p.pizza_CrustType='Pan'
  AND d.discount_DiscountName='Specialty Pizza';

-- ----------------------
-- Pizza B (Small Original)
-- ----------------------
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('Small','Original', @o2, 'completed', CONCAT(YEAR(CURDATE()),'-02-03 12:05:00'), 6.93, 1.40);

-- Regular Cheese
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Regular Cheese'
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Small' AND p.pizza_CrustType='Original';

-- Chicken
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Chicken'
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Small' AND p.pizza_CrustType='Original';

-- Banana Peppers
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Banana Peppers'
WHERE p.ordertable_OrderID=@o2 AND p.pizza_Size='Small' AND p.pizza_CrustType='Original';

-- Order-level $1 off (Lunch Special Medium)
INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID)
SELECT @o2, d.discount_DiscountID
FROM discount d WHERE d.discount_DiscountName='Lunch Special Medium';

-- Recompute totals for order #2 (same formula pattern)
UPDATE ordertable o
SET o.ordertable_CustPrice = ROUND(
        (
            COALESCE((
                SELECT SUM(p.pizza_CustPrice)
                FROM pizza p
                WHERE p.ordertable_OrderID=o.ordertable_OrderID
            ), 0)
            - COALESCE((
                SELECT SUM(d.discount_Amount)
                FROM order_discount od
                JOIN discount d USING(discount_DiscountID)
                WHERE od.ordertable_OrderID=o.ordertable_OrderID
                  AND d.discount_IsPercent=0
            ), 0)
        )
        * EXP(COALESCE((
            SELECT SUM(LN(1 - (d.discount_Amount/100)))
            FROM order_discount od
            JOIN discount d USING(discount_DiscountID)
            WHERE od.ordertable_OrderID=o.ordertable_OrderID
              AND d.discount_IsPercent=1
        ), 0)), 2),
    o.ordertable_BusPrice = ROUND(
        COALESCE((
            SELECT SUM(p.pizza_BusPrice)
            FROM pizza p
            WHERE p.ordertable_OrderID=o.ordertable_OrderID
        ), 0), 2)
WHERE o.ordertable_OrderID=@o2;

-- ------------------------------------------------------------
-- ORDER #3 : Pickup, many identical large original pizzas
-- Customer: Andrew Wilkes-Krier
-- ------------------------------------------------------------
INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime,
                        ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete)
SELECT c.customer_CustID, 'pickup', CONCAT(YEAR(CURDATE()),'-01-03 21:30:00'), 0.00, 0.00, 1
FROM customer c WHERE c.customer_FName='Andrew' AND c.customer_LName='Wilkes-Krier';
SET @o3 := LAST_INSERT_ID();

-- Pickup record, marked as picked up (1)
INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES
(@o3, 1);

-- Bulk insert of 6 identical pizzas
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES 
('Large','Original', @o3, 'completed', CONCAT(YEAR(CURDATE()),'-01-03 21:30:00'), 14.88, 3.30),
('Large','Original', @o3, 'completed', CONCAT(YEAR(CURDATE()),'-01-03 21:30:00'), 14.88, 3.30),
('Large','Original', @o3, 'completed', CONCAT(YEAR(CURDATE()),'-01-03 21:30:00'), 14.88, 3.30),
('Large','Original', @o3, 'completed', CONCAT(YEAR(CURDATE()),'-01-03 21:30:00'), 14.88, 3.30),
('Large','Original', @o3, 'completed', CONCAT(YEAR(CURDATE()),'-01-03 21:30:00'), 14.88, 3.30),
('Large','Original', @o3, 'completed', CONCAT(YEAR(CURDATE()),'-01-03 21:30:00'), 14.88, 3.30);

-- All pizzas get Regular Cheese
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Regular Cheese'
WHERE p.ordertable_OrderID=@o3;

-- All pizzas get Pepperoni
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName='Pepperoni'
WHERE p.ordertable_OrderID=@o3;

-- Recompute totals for order #3
UPDATE ordertable o
SET o.ordertable_CustPrice = ROUND(
        (
            COALESCE((
                SELECT SUM(p.pizza_CustPrice)
                FROM pizza p
                WHERE p.ordertable_OrderID=o.ordertable_OrderID
            ), 0)
            - COALESCE((
                SELECT SUM(d.discount_Amount)
                FROM order_discount od
                JOIN discount d USING(discount_DiscountID)
                WHERE od.ordertable_OrderID=o.ordertable_OrderID
                  AND d.discount_IsPercent=0
            ), 0)
        )
        * EXP(COALESCE((
            SELECT SUM(LN(1 - (d.discount_Amount/100)))
            FROM order_discount od
            JOIN discount d USING(discount_DiscountID)
            WHERE od.ordertable_OrderID=o.ordertable_OrderID
              AND d.discount_IsPercent=1
        ), 0)), 2),
    o.ordertable_BusPrice = ROUND(
        COALESCE((
            SELECT SUM(p.pizza_BusPrice)
            FROM pizza p
            WHERE p.ordertable_OrderID=o.ordertable_OrderID
        ), 0), 2)
WHERE o.ordertable_OrderID=@o3;

-- ------------------------------------------------------------
-- ORDER #4 : Delivery party order with multiple XL pizzas
-- Customer: Andrew Wilkes-Krier
-- ------------------------------------------------------------
INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime,
                        ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete)
SELECT c.customer_CustID, 'delivery', CONCAT(YEAR(CURDATE()),'-02-20 19:11:00'), 0.00, 0.00, 1
FROM customer c WHERE c.customer_FName='Andrew' AND c.customer_LName='Wilkes-Krier';
SET @o4 := LAST_INSERT_ID();

-- Delivery info (Party Blvd)
INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) VALUES
(@o4, 115, 'Party Blvd', 'Anderson', 'SC', 29621, 1);

-- Pizza A: XL Original (Four Cheese Blend, Pepperoni, Sausage)
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('XLarge','Original', @o4, 'completed', CONCAT(YEAR(CURDATE()),'-02-20 19:11:00'), 27.94, 5.59);

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName IN ('Four Cheese Blend','Pepperoni','Sausage')
WHERE p.ordertable_OrderID=@o4 AND p.pizza_CustPrice=27.94;

-- Pizza B: XL Original (Hawaiian style) with Specialty discount
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('XLarge','Original', @o4, 'completed', CONCAT(YEAR(CURDATE()),'-02-20 19:11:00'), 31.50, 6.25);

-- Toppings: Four Cheese Blend, Ham, Pineapple (double Ham and Pineapple)
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID,
       CASE WHEN t.topping_TopName IN ('Ham','Pineapple') THEN 1 ELSE 0 END
FROM pizza p
JOIN topping t ON t.topping_TopName IN ('Four Cheese Blend','Ham','Pineapple')
WHERE p.ordertable_OrderID=@o4 AND p.pizza_CustPrice=31.50;

-- Apply Specialty Pizza discount on Pizza B
INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
SELECT p.pizza_PizzaID, d.discount_DiscountID
FROM pizza p, discount d
WHERE p.ordertable_OrderID=@o4 AND p.pizza_CustPrice=31.50
  AND d.discount_DiscountName='Specialty Pizza';

-- Pizza C: XL Original (Chicken, Bacon, Four Cheese Blend)
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('XLarge','Original', @o4, 'completed', CONCAT(YEAR(CURDATE()),'-02-20 19:11:00'), 26.75, 5.55);

-- Toppings: Four Cheese Blend, Chicken, Bacon (all single)
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName IN ('Four Cheese Blend','Chicken','Bacon')
WHERE p.ordertable_OrderID=@o4 AND p.pizza_CustPrice=26.75;

-- Order-level percentage discount: Gameday Special
INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID)
SELECT @o4, d.discount_DiscountID FROM discount d WHERE d.discount_DiscountName='Gameday Special';

-- Recompute totals for order #4
UPDATE ordertable o
SET o.ordertable_CustPrice = ROUND(
        (
            COALESCE((
                SELECT SUM(p.pizza_CustPrice)
                FROM pizza p
                WHERE p.ordertable_OrderID=o.ordertable_OrderID
            ), 0)
            - COALESCE((
                SELECT SUM(d.discount_Amount)
                FROM order_discount od
                JOIN discount d USING(discount_DiscountID)
                WHERE od.ordertable_OrderID=o.ordertable_OrderID
                  AND d.discount_IsPercent=0
            ), 0)
        )
        * EXP(COALESCE((
            SELECT SUM(LN(1 - (d.discount_Amount/100)))
            FROM order_discount od
            JOIN discount d USING(discount_DiscountID)
            WHERE od.ordertable_OrderID=o.ordertable_OrderID
              AND d.discount_IsPercent=1
        ), 0)), 2),
    o.ordertable_BusPrice = ROUND(
        COALESCE((
            SELECT SUM(p.pizza_BusPrice)
            FROM pizza p
            WHERE p.ordertable_OrderID=o.ordertable_OrderID
        ), 0), 2)
WHERE o.ordertable_OrderID=@o4;

-- ------------------------------------------------------------
-- ORDER #5 : Pickup, one XL Gluten-Free specialty veggie pizza
-- Customer: Matt Engers
-- ------------------------------------------------------------
INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime,
                        ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete)
SELECT c.customer_CustID, 'pickup', CONCAT(YEAR(CURDATE()),'-01-02 17:30:00'), 0.00, 0.00, 1
FROM customer c WHERE c.customer_FName='Matt' AND c.customer_LName='Engers';
SET @o5 := LAST_INSERT_ID();

-- Pickup record
INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp) VALUES
(@o5, 1);

-- XL Gluten-Free pizza with many veggies + goat cheese
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('XLarge','Gluten-Free', @o5, 'completed', CONCAT(YEAR(CURDATE()),'-01-02 17:30:00'), 28.70, 7.84);

-- Add toppings to the XL GF pizza
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 0
FROM pizza p
JOIN topping t ON t.topping_TopName IN ('Goat Cheese','Green Pepper','Onion','Roma Tomato','Mushrooms','Black Olives')
WHERE p.ordertable_OrderID=@o5;

-- Apply Specialty Pizza discount
INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
SELECT p.pizza_PizzaID, d.discount_DiscountID
FROM pizza p, discount d
WHERE p.ordertable_OrderID=@o5 AND d.discount_DiscountName='Specialty Pizza';

-- Recompute totals for order #5
UPDATE ordertable o
SET o.ordertable_CustPrice = ROUND(
        (
            COALESCE((
                SELECT SUM(p.pizza_CustPrice)
                FROM pizza p
                WHERE p.ordertable_OrderID=o.ordertable_OrderID
            ), 0)
            - COALESCE((
                SELECT SUM(d.discount_Amount)
                FROM order_discount od
                JOIN discount d USING(discount_DiscountID)
                WHERE od.ordertable_OrderID=o.ordertable_OrderID
                  AND d.discount_IsPercent=0
            ), 0)
        )
        * EXP(COALESCE((
            SELECT SUM(LN(1 - (d.discount_Amount/100)))
            FROM order_discount od
            JOIN discount d USING(discount_DiscountID)
            WHERE od.ordertable_OrderID=o.ordertable_OrderID
              AND d.discount_IsPercent=1
        ), 0)), 2),
    o.ordertable_BusPrice = ROUND(
        COALESCE((
            SELECT SUM(p.pizza_BusPrice)
            FROM pizza p
            WHERE p.ordertable_OrderID=o.ordertable_OrderID
        ), 0), 2)
WHERE o.ordertable_OrderID=@o5;

-- ------------------------------------------------------------
-- ORDER #6 : Delivery, one large thin meat/veggie pizza
-- Customer: Frank Turner
-- ------------------------------------------------------------
INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime,
                        ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete)
SELECT c.customer_CustID, 'delivery', CONCAT(YEAR(CURDATE()),'-01-02 18:17:00'), 0.00, 0.00, 1
FROM customer c WHERE c.customer_FName='Frank' AND c.customer_LName='Turner';
SET @o6 := LAST_INSERT_ID();

-- Delivery address for Frank
INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) VALUES
(@o6, 6745, 'Wessex St', 'Anderson', 'SC', 29621, 1);

-- Large thin crust pizza
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('Large','Thin', @o6, 'completed', CONCAT(YEAR(CURDATE()),'-01-02 18:17:00'), 25.81, 3.64);

-- Toppings: Chicken, Green Pepper, Onion, Mushrooms, Four Cheese Blend (double cheese)
INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID,
       CASE WHEN t.topping_TopName='Four Cheese Blend' THEN 1 ELSE 0 END
FROM pizza p
JOIN topping t ON t.topping_TopName IN ('Chicken','Green Pepper','Onion','Mushrooms','Four Cheese Blend')
WHERE p.ordertable_OrderID=@o6;

-- Recompute totals for order #6
UPDATE ordertable o
SET o.ordertable_CustPrice = ROUND(
        (
            COALESCE((
                SELECT SUM(p.pizza_CustPrice)
                FROM pizza p
                WHERE p.ordertable_OrderID=o.ordertable_OrderID
            ), 0)
            - COALESCE((
                SELECT SUM(d.discount_Amount)
                FROM order_discount od
                JOIN discount d USING(discount_DiscountID)
                WHERE od.ordertable_OrderID=o.ordertable_OrderID
                  AND d.discount_IsPercent=0
            ), 0)
        )
        * EXP(COALESCE((
            SELECT SUM(LN(1 - (d.discount_Amount/100)))
            FROM order_discount od
            JOIN discount d USING(discount_DiscountID)
            WHERE od.ordertable_OrderID=o.ordertable_OrderID
              AND d.discount_IsPercent=1
        ), 0)), 2),
    o.ordertable_BusPrice = ROUND(
        COALESCE((
            SELECT SUM(p.pizza_BusPrice)
            FROM pizza p
            WHERE p.ordertable_OrderID=o.ordertable_OrderID
        ), 0), 2)
WHERE o.ordertable_OrderID=@o6;

-- ------------------------------------------------------------
-- ORDER #7 : Delivery, two large thin pizzas
-- Customer: Milo Auckerman
-- ------------------------------------------------------------
INSERT INTO ordertable (customer_CustID, ordertable_OrderType, ordertable_OrderDateTime,
                        ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete)
SELECT c.customer_CustID, 'delivery', CONCAT(YEAR(CURDATE()),'-02-13 20:32:00'), 0.00, 0.00, 1
FROM customer c WHERE c.customer_FName='Milo' AND c.customer_LName='Auckerman';
SET @o7 := LAST_INSERT_ID();

-- Delivery row for Milo
INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered) VALUES
(@o7, 8879, 'Suburban Lane', 'Anderson', 'SC', 29621, 1);

-- Pizza A: large thin with Four Cheese Blend (double)
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('Large','Thin', @o7, 'completed', CONCAT(YEAR(CURDATE()),'-02-13 20:32:00'), 18.00, 2.75);

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID, 1
FROM pizza p
JOIN topping t ON t.topping_TopName='Four Cheese Blend'
WHERE p.ordertable_OrderID=@o7 AND p.pizza_CustPrice=18.00;

-- Pizza B: large thin with Regular Cheese + double Pepperoni
INSERT INTO pizza (pizza_Size, pizza_CrustType, ordertable_OrderID, pizza_PizzaState, pizza_PizzaDate, pizza_CustPrice, pizza_BusPrice)
VALUES ('Large','Thin', @o7, 'completed', CONCAT(YEAR(CURDATE()),'-02-13 20:32:00'), 19.25, 3.25);

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT p.pizza_PizzaID, t.topping_TopID,
       CASE WHEN t.topping_TopName='Pepperoni' THEN 1 ELSE 0 END
FROM pizza p
JOIN topping t ON t.topping_TopName IN ('Regular Cheese','Pepperoni')
WHERE p.ordertable_OrderID=@o7 AND p.pizza_CustPrice=19.25;

-- Order-level Employee discount (15%)
INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID)
SELECT @o7, d.discount_DiscountID FROM discount d WHERE d.discount_DiscountName='Employee';

-- Recompute totals for order #7
UPDATE ordertable o
SET o.ordertable_CustPrice = ROUND(
        (
            COALESCE((
                SELECT SUM(p.pizza_CustPrice)
                FROM pizza p
                WHERE p.ordertable_OrderID=o.ordertable_OrderID
            ), 0)
            - COALESCE((
                SELECT SUM(d.discount_Amount)
                FROM order_discount od
                JOIN discount d USING(discount_DiscountID)
                WHERE od.ordertable_OrderID=o.ordertable_OrderID
                  AND d.discount_IsPercent=0
            ), 0)
        )
        * EXP(COALESCE((
            SELECT SUM(LN(1 - (d.discount_Amount/100)))
            FROM order_discount od
            JOIN discount d USING(discount_DiscountID)
            WHERE od.ordertable_OrderID=o.ordertable_OrderID
              AND d.discount_IsPercent=1
        ), 0)), 2),
    o.ordertable_BusPrice = ROUND(
        COALESCE((
            SELECT SUM(p.pizza_BusPrice)
            FROM pizza p
            WHERE p.ordertable_OrderID=o.ordertable_OrderID
        ), 0), 2)
WHERE o.ordertable_OrderID=@o7;

-- ============================================================
-- Final step:
--   Move all staged toppings from TempPizzaToppings into
--   the real pizza_topping table, then drop the temp table.
-- ============================================================
INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT pizza_PizzaID, topping_TopID, pizza_topping_IsDouble
FROM TempPizzaToppings;

DROP TEMPORARY TABLE TempPizzaToppings;

