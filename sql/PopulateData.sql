-- ============================================================
-- PopulateData.sql  (MySQL-friendly, advanced style)
-- ============================================================

INSERT INTO baseprice (baseprice_Size, baseprice_CrustType, baseprice_CustPrice, baseprice_BusPrice)
WITH base_price_data AS (
    SELECT 'Small' AS SIZE, 'Thin' AS crust, 3.00 AS cust_price, 0.50 AS bus_price
    UNION ALL SELECT 'Small','Original',     3.00, 0.75
    UNION ALL SELECT 'Small','Pan',          3.50, 1.00
    UNION ALL SELECT 'Small','Gluten-Free',  4.00, 2.00
    UNION ALL SELECT 'Medium','Thin',        5.00, 1.00
    UNION ALL SELECT 'Medium','Original',    5.00, 1.50
    UNION ALL SELECT 'Medium','Pan',         6.00, 2.25
    UNION ALL SELECT 'Medium','Gluten-Free', 6.25, 3.00
    UNION ALL SELECT 'Large','Thin',         8.00, 1.25
    UNION ALL SELECT 'Large','Original',     8.00, 2.00
    UNION ALL SELECT 'Large','Pan',          9.00, 3.00
    UNION ALL SELECT 'Large','Gluten-Free',  9.50, 4.00
    UNION ALL SELECT 'XLarge','Thin',       10.00, 2.00
    UNION ALL SELECT 'XLarge','Original',   10.00, 3.00
    UNION ALL SELECT 'XLarge','Pan',        11.50, 4.50
    UNION ALL SELECT 'XLarge','Gluten-Free',12.50, 6.00
)
SELECT SIZE, crust, cust_price, bus_price
FROM base_price_data;

INSERT INTO topping (
    topping_TopName,
    topping_SmallAMT,
    topping_MedAMT,
    topping_LgAMT,
    topping_XLAMT,
    topping_CustPrice,
    topping_BusPrice,
    topping_MinINVT,
    topping_CurINVT
)
WITH topping_data AS (
    SELECT 'Pepperoni' AS name,         2.00 AS small, 2.75 AS medium, 3.50 AS LARGE, 4.50 AS xlarge, 1.25 AS cust_price, 0.20 AS bus_price, 50 AS min_inv, 100 AS cur_inv
    UNION ALL SELECT 'Sausage',           2.50, 3.00, 3.50, 4.25, 1.25, 0.15, 50, 100
    UNION ALL SELECT 'Ham',               2.00, 2.50, 3.25, 4.00, 1.50, 0.15, 25,  78
    UNION ALL SELECT 'Chicken',           1.50, 2.00, 2.25, 3.00, 1.75, 0.25, 25,  56
    UNION ALL SELECT 'Green Pepper',      1.00, 1.50, 2.00, 2.50, 0.50, 0.02, 25,  79
    UNION ALL SELECT 'Onion',             1.00, 1.50, 2.00, 2.75, 0.50, 0.02, 25,  85
    UNION ALL SELECT 'Roma Tomato',       2.00, 3.00, 3.50, 4.50, 0.75, 0.03, 10,  86
    UNION ALL SELECT 'Mushrooms',         1.50, 2.00, 2.50, 3.00, 0.75, 0.10, 50,  52
    UNION ALL SELECT 'Black Olives',      0.75, 1.00, 1.50, 2.00, 0.60, 0.10, 25,  39
    UNION ALL SELECT 'Pineapple',         1.00, 1.25, 1.75, 2.00, 1.00, 0.25,  0,  15
    UNION ALL SELECT 'Jalapenos',         0.50, 0.75, 1.25, 1.75, 0.50, 0.05,  0,  64
    UNION ALL SELECT 'Banana Peppers',    0.60, 1.00, 1.30, 1.75, 0.50, 0.05,  0,  36
    UNION ALL SELECT 'Regular Cheese',    2.00, 3.50, 5.00, 7.00, 0.50, 0.12, 50, 250
    UNION ALL SELECT 'Four Cheese Blend', 2.00, 3.50, 5.00, 7.00, 1.00, 0.15, 25, 150
    UNION ALL SELECT 'Feta Cheese',       1.75, 3.00, 4.00, 5.50, 1.50, 0.18,  0,  75
    UNION ALL SELECT 'Goat Cheese',       1.60, 2.75, 4.00, 5.50, 1.50, 0.20,  0,  54
    UNION ALL SELECT 'Bacon',             1.00, 1.50, 2.00, 3.00, 1.50, 0.25,  0,  89
)
SELECT
    name,
    small, medium, LARGE, xlarge,
    cust_price, bus_price, min_inv, cur_inv
FROM topping_data;

DROP TEMPORARY TABLE IF EXISTS TempPizzaToppings;

CREATE TEMPORARY TABLE TempPizzaToppings (
    pizza_PizzaID INT NOT NULL,
    topping_TopID INT NOT NULL,
    pizza_topping_IsDouble INT NOT NULL,
    INDEX idx_temp_pizza (pizza_PizzaID),
    INDEX idx_temp_topping (topping_TopID)
) ENGINE=MEMORY;

INSERT INTO discount (discount_DiscountName, discount_Amount, discount_IsPercent)
WITH discount_data AS (
    SELECT 'Employee' AS name,             15.00 AS amount, 1 AS is_percent
    UNION ALL SELECT 'Lunch Special Medium',  1.00, 0
    UNION ALL SELECT 'Lunch Special Large',   2.00, 0
    UNION ALL SELECT 'Specialty Pizza',       1.50, 0
    UNION ALL SELECT 'Happy Hour',           10.00, 1
    UNION ALL SELECT 'Gameday Special',      20.00, 1
)
SELECT name, amount, is_percent FROM discount_data;

INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum)
WITH customer_data AS (
    SELECT 'Andrew' AS fname, 'Wilkes-Krier' AS lname, '8642545861' AS phone
    UNION ALL SELECT 'Matt', 'Engers', '8644749953'
    UNION ALL SELECT 'Frank', 'Turner', '8642328944'
    UNION ALL SELECT 'Milo', 'Auckerman', '8648785679'
)
SELECT fname, lname, phone FROM customer_data;

DELIMITER //

CREATE PROCEDURE recalc_order_totals(IN p_order_id INT)
BEGIN
    WITH order_pizza_summary AS (
        SELECT
            ordertable_OrderID,
            SUM(pizza_CustPrice) AS total_cust_price,
            SUM(pizza_BusPrice) AS total_bus_price
        FROM pizza
        WHERE ordertable_OrderID = p_order_id
        GROUP BY ordertable_OrderID
    ),
    order_discount_summary AS (
        SELECT
            od.ordertable_OrderID,
            SUM(CASE WHEN d.discount_IsPercent = 0 THEN d.discount_Amount ELSE 0 END) AS total_flat_discount,
            SUM(CASE WHEN d.discount_IsPercent = 1 THEN LN(1 - (d.discount_Amount/100)) ELSE 0 END) AS total_percent_factor
        FROM order_discount od
        JOIN discount d USING(discount_DiscountID)
        WHERE od.ordertable_OrderID = p_order_id
        GROUP BY od.ordertable_OrderID
    )
    UPDATE ordertable o
    LEFT JOIN order_pizza_summary ops ON o.ordertable_OrderID = ops.ordertable_OrderID
    LEFT JOIN order_discount_summary ods ON o.ordertable_OrderID = ods.ordertable_OrderID
    SET o.ordertable_CustPrice = ROUND(
            COALESCE(ops.total_cust_price, 0) -
            COALESCE(ods.total_flat_discount, 0) *
            EXP(COALESCE(ods.total_percent_factor, 0)), 2),
        o.ordertable_BusPrice = ROUND(COALESCE(ops.total_bus_price, 0), 2)
    WHERE o.ordertable_OrderID = p_order_id;
END //

DELIMITER ;

SET @current_year = YEAR(CURDATE());

------------------------------------------------------------
-- ORDER #1 : Dine-in, one large thin pizza
------------------------------------------------------------
INSERT INTO ordertable (
    customer_CustID,
    ordertable_OrderType,
    ordertable_OrderDateTime,
    ordertable_CustPrice,
    ordertable_BusPrice,
    ordertable_IsComplete
)
SELECT
    NULL,
    'dinein',
    CONCAT(@current_year, '-01-05 12:03:00'),
    0.00,
    0.00,
    0;   -- initially NOT complete

SELECT LAST_INSERT_ID() INTO @o1;

INSERT INTO dinein (ordertable_OrderID, dinein_TableNum)
SELECT @o1, 21;

INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'Large',
    'Thin',
    @o1,
    'completed',
    CONCAT(@current_year, '-01-05 12:03:00'),
    19.75,
    3.68;

SELECT LAST_INSERT_ID() INTO @p1;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT @p1, topping_TopID, 1 FROM topping WHERE topping_TopName='Regular Cheese'
UNION ALL
SELECT @p1, topping_TopID, 0 FROM topping WHERE topping_TopName='Pepperoni'
UNION ALL
SELECT @p1, topping_TopID, 0 FROM topping WHERE topping_TopName='Sausage';

INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
SELECT @p1, discount_DiscountID
FROM discount
WHERE discount_DiscountName='Lunch Special Large';

CALL recalc_order_totals(@o1);

UPDATE ordertable
SET ordertable_IsComplete = 1
WHERE ordertable_OrderID = @o1;

------------------------------------------------------------
-- ORDER #2 : Dine-in, two pizzas
------------------------------------------------------------
INSERT INTO ordertable (
    customer_CustID,
    ordertable_OrderType,
    ordertable_OrderDateTime,
    ordertable_CustPrice,
    ordertable_BusPrice,
    ordertable_IsComplete
)
SELECT
    NULL,
    'dinein',
    CONCAT(@current_year, '-02-03 12:05:00'),
    0.00,
    0.00,
    0;

SELECT LAST_INSERT_ID() INTO @o2;

INSERT INTO dinein (ordertable_OrderID, dinein_TableNum)
SELECT @o2, 4;

-- Pizza A (Medium Pan)
INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'Medium',
    'Pan',
    @o2,
    'completed',
    CONCAT(@current_year, '-02-03 12:05:00'),
    13.85,
    3.23;

SELECT LAST_INSERT_ID() INTO @pizza_a_id;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT @pizza_a_id, topping_TopID, 0
FROM topping
WHERE topping_TopName IN ('Feta Cheese', 'Black Olives', 'Roma Tomato', 'Mushrooms', 'Banana Peppers');

-- Pizza B (Small Original)
INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'Small',
    'Original',
    @o2,
    'completed',
    CONCAT(@current_year, '-02-03 12:05:00'),
    6.93,
    1.40;

SELECT LAST_INSERT_ID() INTO @pizza_b_id;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT @pizza_b_id, topping_TopID, 0
FROM topping
WHERE topping_TopName IN ('Regular Cheese', 'Chicken', 'Banana Peppers');

INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
SELECT p.pizza_PizzaID, d.discount_DiscountID
FROM pizza p
CROSS JOIN discount d
WHERE p.ordertable_OrderID = @o2
  AND p.pizza_Size = 'Medium'
  AND p.pizza_CrustType = 'Pan'
  AND d.discount_DiscountName = 'Specialty Pizza';

INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID)
SELECT @o2, discount_DiscountID
FROM discount
WHERE discount_DiscountName = 'Lunch Special Medium';

CALL recalc_order_totals(@o2);

UPDATE ordertable
SET ordertable_IsComplete = 1
WHERE ordertable_OrderID = @o2;

------------------------------------------------------------
-- ORDER #3 : Pickup, 6 large original pizzas (Andrew)
------------------------------------------------------------
SELECT customer_CustID
INTO @cust_3
FROM customer
WHERE customer_FName='Andrew' AND customer_LName='Wilkes-Krier'
LIMIT 1;

INSERT INTO ordertable (
    customer_CustID,
    ordertable_OrderType,
    ordertable_OrderDateTime,
    ordertable_CustPrice,
    ordertable_BusPrice,
    ordertable_IsComplete
)
SELECT
    @cust_3,
    'pickup',
    CONCAT(@current_year, '-01-03 21:30:00'),
    0.00,
    0.00,
    0;

SELECT LAST_INSERT_ID() INTO @o3;

INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp)
SELECT @o3, 1;

-- generate 6 pizzas via derived table (no WITH here)
INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'Large',
    'Original',
    @o3,
    'completed',
    CONCAT(@current_year, '-01-03 21:30:00'),
    14.88,
    3.30
FROM (
    SELECT 1 AS n UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5 UNION ALL
    SELECT 6
) AS gen;

SELECT LAST_INSERT_ID() INTO @first_pizza_3;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT
    @first_pizza_3 + d.delta AS pizza_id,
    t.topping_TopID,
    0 AS is_double
FROM topping t
JOIN (
    SELECT 0 AS delta UNION ALL
    SELECT 1 UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5
) AS d
WHERE t.topping_TopName IN ('Regular Cheese', 'Pepperoni');

CALL recalc_order_totals(@o3);

UPDATE ordertable
SET ordertable_IsComplete = 1
WHERE ordertable_OrderID = @o3;

------------------------------------------------------------
-- ORDER #4 : Delivery, 3 XL originals (Andrew, party)
------------------------------------------------------------
SELECT customer_CustID
INTO @cust_4
FROM customer
WHERE customer_FName='Andrew' AND customer_LName='Wilkes-Krier'
LIMIT 1;

INSERT INTO ordertable (
    customer_CustID,
    ordertable_OrderType,
    ordertable_OrderDateTime,
    ordertable_CustPrice,
    ordertable_BusPrice,
    ordertable_IsComplete
)
SELECT
    @cust_4,
    'delivery',
    CONCAT(@current_year, '-02-20 19:11:00'),
    0.00,
    0.00,
    0;

SELECT LAST_INSERT_ID() INTO @o4;

INSERT INTO delivery (
    ordertable_OrderID,
    delivery_HouseNum,
    delivery_Street,
    delivery_City,
    delivery_State,
    delivery_Zip,
    delivery_IsDelivered
)
SELECT @o4, 115, 'Party Blvd', 'Anderson', 'SC', 29621, 1;

-- Pizza A
INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'XLarge',
    'Original',
    @o4,
    'completed',
    CONCAT(@current_year, '-02-20 19:11:00'),
    27.94,
    5.59;

SELECT LAST_INSERT_ID() INTO @pizza4_a;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT @pizza4_a, t.topping_TopID, 0
FROM topping t
WHERE t.topping_TopName IN ('Four Cheese Blend','Pepperoni','Sausage');

-- Pizza B
INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'XLarge',
    'Original',
    @o4,
    'completed',
    CONCAT(@current_year, '-02-20 19:11:00'),
    31.50,
    6.25;

SELECT LAST_INSERT_ID() INTO @pizza4_b;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT
    @pizza4_b,
    t.topping_TopID,
    CASE WHEN t.topping_TopName IN ('Ham','Pineapple') THEN 1 ELSE 0 END AS is_double
FROM topping t
WHERE t.topping_TopName IN ('Four Cheese Blend','Ham','Pineapple');

-- Pizza C
INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'XLarge',
    'Original',
    @o4,
    'completed',
    CONCAT(@current_year, '-02-20 19:11:00'),
    26.75,
    5.55;

SELECT LAST_INSERT_ID() INTO @pizza4_c;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT @pizza4_c, t.topping_TopID, 0
FROM topping t
WHERE t.topping_TopName IN ('Four Cheese Blend','Chicken','Bacon');

INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
SELECT p.pizza_PizzaID, d.discount_DiscountID
FROM pizza p
CROSS JOIN discount d
WHERE p.ordertable_OrderID = @o4
  AND p.pizza_CustPrice = 31.50
  AND d.discount_DiscountName = 'Specialty Pizza';

INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID)
SELECT @o4, discount_DiscountID
FROM discount
WHERE discount_DiscountName = 'Gameday Special';

CALL recalc_order_totals(@o4);

UPDATE ordertable
SET ordertable_IsComplete = 1
WHERE ordertable_OrderID = @o4;

------------------------------------------------------------
-- ORDER #5 : Pickup, XL GF veggie (Matt)
------------------------------------------------------------
SELECT customer_CustID
INTO @cust_5
FROM customer
WHERE customer_FName='Matt' AND customer_LName='Engers'
LIMIT 1;

INSERT INTO ordertable (
    customer_CustID,
    ordertable_OrderType,
    ordertable_OrderDateTime,
    ordertable_CustPrice,
    ordertable_BusPrice,
    ordertable_IsComplete
)
SELECT
    @cust_5,
    'pickup',
    CONCAT(@current_year, '-01-02 17:30:00'),
    0.00,
    0.00,
    0;

SELECT LAST_INSERT_ID() INTO @o5;

INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp)
SELECT @o5, 1;

INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'XLarge',
    'Gluten-Free',
    @o5,
    'completed',
    CONCAT(@current_year, '-01-02 17:30:00'),
    28.70,
    7.84;

SELECT LAST_INSERT_ID() INTO @pizza5;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT @pizza5, t.topping_TopID, 0
FROM topping t
WHERE t.topping_TopName IN (
    'Goat Cheese','Green Pepper','Onion',
    'Roma Tomato','Mushrooms','Black Olives'
);

INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
SELECT p.pizza_PizzaID, d.discount_DiscountID
FROM pizza p
CROSS JOIN discount d
WHERE p.ordertable_OrderID = @o5
  AND d.discount_DiscountName = 'Specialty Pizza';

CALL recalc_order_totals(@o5);

UPDATE ordertable
SET ordertable_IsComplete = 1
WHERE ordertable_OrderID = @o5;

------------------------------------------------------------
-- ORDER #6 : Delivery, large thin (Frank)
------------------------------------------------------------
SELECT customer_CustID
INTO @cust_6
FROM customer
WHERE customer_FName='Frank' AND customer_LName='Turner'
LIMIT 1;

INSERT INTO ordertable (
    customer_CustID,
    ordertable_OrderType,
    ordertable_OrderDateTime,
    ordertable_CustPrice,
    ordertable_BusPrice,
    ordertable_IsComplete
)
SELECT
    @cust_6,
    'delivery',
    CONCAT(@current_year, '-01-02 18:17:00'),
    0.00,
    0.00,
    0;

SELECT LAST_INSERT_ID() INTO @o6;

INSERT INTO delivery (
    ordertable_OrderID,
    delivery_HouseNum,
    delivery_Street,
    delivery_City,
    delivery_State,
    delivery_Zip,
    delivery_IsDelivered
)
SELECT @o6, 6745, 'Wessex St', 'Anderson', 'SC', 29621, 1;

INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'Large',
    'Thin',
    @o6,
    'completed',
    CONCAT(@current_year, '-01-02 18:17:00'),
    25.81,
    3.64;

SELECT LAST_INSERT_ID() INTO @pizza6;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT
    @pizza6,
    t.topping_TopID,
    CASE WHEN t.topping_TopName='Four Cheese Blend' THEN 1 ELSE 0 END AS is_double
FROM topping t
WHERE t.topping_TopName IN (
    'Chicken','Green Pepper','Onion','Mushrooms','Four Cheese Blend'
);

CALL recalc_order_totals(@o6);

UPDATE ordertable
SET ordertable_IsComplete = 1
WHERE ordertable_OrderID = @o6;

------------------------------------------------------------
-- ORDER #7 : Delivery, two larges (Milo, Employee discount)
------------------------------------------------------------
SELECT customer_CustID
INTO @cust_7
FROM customer
WHERE customer_FName='Milo' AND customer_LName='Auckerman'
LIMIT 1;

INSERT INTO ordertable (
    customer_CustID,
    ordertable_OrderType,
    ordertable_OrderDateTime,
    ordertable_CustPrice,
    ordertable_BusPrice,
    ordertable_IsComplete
)
SELECT
    @cust_7,
    'delivery',
    CONCAT(@current_year, '-02-13 20:32:00'),
    0.00,
    0.00,
    0;

SELECT LAST_INSERT_ID() INTO @o7;

INSERT INTO delivery (
    ordertable_OrderID,
    delivery_HouseNum,
    delivery_Street,
    delivery_City,
    delivery_State,
    delivery_Zip,
    delivery_IsDelivered
)
SELECT @o7, 8879, 'Suburban Lane', 'Anderson', 'SC', 29621, 1;

-- Pizza A
INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'Large',
    'Thin',
    @o7,
    'completed',
    CONCAT(@current_year, '-02-13 20:32:00'),
    18.00,
    2.75;

SELECT LAST_INSERT_ID() INTO @pizza7_a;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT @pizza7_a, t.topping_TopID, 1
FROM topping t
WHERE t.topping_TopName='Four Cheese Blend';

-- Pizza B
INSERT INTO pizza (
    pizza_Size,
    pizza_CrustType,
    ordertable_OrderID,
    pizza_PizzaState,
    pizza_PizzaDate,
    pizza_CustPrice,
    pizza_BusPrice
)
SELECT
    'Large',
    'Thin',
    @o7,
    'completed',
    CONCAT(@current_year, '-02-13 20:32:00'),
    19.25,
    3.25;

SELECT LAST_INSERT_ID() INTO @pizza7_b;

INSERT INTO TempPizzaToppings (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT
    @pizza7_b,
    t.topping_TopID,
    CASE WHEN t.topping_TopName='Pepperoni' THEN 1 ELSE 0 END AS is_double
FROM topping t
WHERE t.topping_TopName IN ('Regular Cheese','Pepperoni');

INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID)
SELECT @o7, discount_DiscountID
FROM discount
WHERE discount_DiscountName = 'Employee';

CALL recalc_order_totals(@o7);

UPDATE ordertable
SET ordertable_IsComplete = 1
WHERE ordertable_OrderID = @o7;

------------------------------------------------------------
-- Move staged toppings into pizza_topping, cleanup
------------------------------------------------------------
INSERT INTO pizza_topping (pizza_PizzaID, topping_TopID, pizza_topping_IsDouble)
SELECT pizza_PizzaID, topping_TopID, pizza_topping_IsDouble
FROM TempPizzaToppings
ORDER BY pizza_PizzaID, topping_TopID;

DROP TEMPORARY TABLE IF EXISTS TempPizzaToppings;
DROP PROCEDURE IF EXISTS recalc_order_totals;
