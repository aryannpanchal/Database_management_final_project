-- ==========================
-- PopulateData.sql (FINAL - FIXED)
-- ==========================

START TRANSACTION;

INSERT INTO discount(discount_DiscountName, discount_Amount, discount_IsPercent) VALUES
('Employee',15,1),
('Gameday Special',20,1),
('Happy Hour',10,1),
('Lunch Special Large',2,0),
('Lunch Special Medium',1,0),
('Specialty Pizza',1.5,0);

INSERT INTO baseprice(baseprice_Size, baseprice_CrustType, baseprice_CustPrice, baseprice_BusPrice) VALUES
('Large','Gluten-Free',9.5,4),
('Large','Original',8,2),
('Large','Pan',9,3),
('Large','Thin',8,1.25),
('Medium','Gluten-Free',6.25,3),
('Medium','Original',5,1.5),
('Medium','Pan',6,2.25),
('Medium','Thin',5,1),
('Small','Gluten-Free',4,2),
('Small','Original',3,0.75),
('Small','Pan',3.5,1),
('Small','Thin',3,0.5),
('XLarge','Gluten-Free',12.5,6),
('XLarge','Original',10,3),
('XLarge','Pan',11.5,4.5),
('XLarge','Thin',10,2);

INSERT INTO topping(topping_TopName, topping_SmallAMT, topping_MedAMT, topping_LgAMT, topping_XLAMT,
topping_CustPrice, topping_BusPrice, topping_MinINVT, topping_CurINVT) VALUES
('Bacon',1,1.5,2,3,1.5,0.25,0,89),
('Banana Peppers',0.6,1,1.3,1.75,0.5,0.05,0,36),
('Black Olives',0.75,1,1.5,2,0.6,0.1,25,39),
('Chicken',1.5,2,2.25,3,1.75,0.25,25,56),
('Feta Cheese',1.75,3,4,5.5,1.5,0.18,0,75),
('Four Cheese Blend',2,3.5,5,7,1,0.15,25,150),
('Goat Cheese',1.6,2.75,4,5.5,1.5,0.2,0,54),
('Green Pepper',1,1.5,2,2.5,0.5,0.02,25,79),
('Ham',2,2.5,3.25,4,1.5,0.15,25,78),
('Jalapenos',0.5,0.75,1.25,1.75,0.5,0.05,0,64),
('Mushrooms',1.5,2,2.5,3,0.75,0.1,50,52),
('Onion',1,1.5,2,2.75,0.5,0.02,25,85),
('Pepperoni',2,2.75,3.5,4.5,1.25,0.2,50,100),
('Pineapple',1,1.25,1.75,2,1,0.25,0,15),
('Regular Cheese',2,3.5,5,7,0.5,0.12,50,250),
('Roma Tomato',2,3,3.5,4.5,0.75,0.03,10,86),
('Sausage',2.5,3,3.5,4.25,1.25,0.15,50,100);

INSERT INTO customer(customer_FName, customer_LName, customer_PhoneNum) VALUES
('Andrew','Wilkes-Krier','8642545861'),
('Frank','Turner','8642328944'),
('Matt','Engers','8644749953'),
('Milo','Auckerman','8648785679');

INSERT INTO ordertable(ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID)
VALUES ('dinein','2025-01-05 12:03:00',19.75,3.68,1,NULL);
SET @ord1 := LAST_INSERT_ID();
INSERT INTO dinein VALUES (@ord1,21);

INSERT INTO ordertable(ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID)
VALUES ('dinein','2025-02-03 12:05:00',19.78,4.63,1,NULL);
SET @ord2 := LAST_INSERT_ID();
INSERT INTO dinein VALUES (@ord2,4);

INSERT INTO ordertable(ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID)
VALUES ('pickup','2025-01-03 21:30:00',89.28,19.80,1,(SELECT customer_CustID FROM customer WHERE customer_FName='Andrew' AND customer_LName='Wilkes-Krier'));
SET @ord3 := LAST_INSERT_ID();
INSERT INTO pickup VALUES (@ord3,1);

INSERT INTO ordertable(ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID)
VALUES ('delivery','2025-02-20 19:11:00',68.95,17.39,1,(SELECT customer_CustID FROM customer WHERE customer_FName='Andrew' AND customer_LName='Wilkes-Krier'));
SET @ord4 := LAST_INSERT_ID();
INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered)
VALUES (@ord4,115,'Party Blvd','Anderson','SC',29621,1);

INSERT INTO ordertable(ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID)
VALUES ('pickup','2025-01-02 17:30:00',28.70,7.84,1,(SELECT customer_CustID FROM customer WHERE customer_FName='Matt' AND customer_LName='Engers'));
SET @ord5 := LAST_INSERT_ID();
INSERT INTO pickup VALUES (@ord5,1);

INSERT INTO ordertable(ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID)
VALUES ('delivery','2025-01-02 18:17:00',25.81,3.64,1,(SELECT customer_CustID FROM customer WHERE customer_FName='Frank' AND customer_LName='Turner'));
SET @ord6 := LAST_INSERT_ID();
INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered)
VALUES (@ord6,6745,'Wessex St','Anderson','SC',29621,1);

INSERT INTO ordertable(ordertable_OrderType, ordertable_OrderDateTime, ordertable_CustPrice, ordertable_BusPrice, ordertable_IsComplete, customer_CustID)
VALUES ('delivery','2025-02-13 20:32:00',31.66,6.00,1,(SELECT customer_CustID FROM customer WHERE customer_FName='Milo' AND customer_LName='Auckerman'));
SET @ord7 := LAST_INSERT_ID();
INSERT INTO delivery (ordertable_OrderID, delivery_HouseNum, delivery_Street, delivery_City, delivery_State, delivery_Zip, delivery_IsDelivered)
VALUES (@ord7,8879,'Suburban Lane','Anderson','SC',29621,1);

-- pizzas
SET @dt3 := '2025-01-03 21:30:00';
INSERT INTO pizza VALUES
(NULL,@ord3,'Large','Original','completed',@dt3,14.88,3.30),
(NULL,@ord3,'Large','Original','completed',@dt3,14.88,3.30),
(NULL,@ord3,'Large','Original','completed',@dt3,14.88,3.30),
(NULL,@ord3,'Large','Original','completed',@dt3,14.88,3.30),
(NULL,@ord3,'Large','Original','completed',@dt3,14.88,3.30),
(NULL,@ord3,'Large','Original','completed',@dt3,14.88,3.30);

INSERT INTO pizza VALUES (NULL,@ord6,'Large','Thin','completed','2025-01-02 18:17:00',25.81,3.64);
INSERT INTO pizza VALUES (NULL,@ord1,'Large','Thin','completed','2025-01-05 12:03:00',19.75,3.68);
INSERT INTO pizza VALUES (NULL,@ord7,'Large','Thin','completed','2025-02-13 20:32:00',18.00,2.75);
INSERT INTO pizza VALUES (NULL,@ord7,'Large','Thin','completed','2025-02-13 20:32:00',19.25,3.25);
INSERT INTO pizza VALUES (NULL,@ord2,'Small','Original','completed','2025-02-03 12:05:00',6.93,1.40);
INSERT INTO pizza VALUES (NULL,@ord2,'Medium','Pan','completed','2025-02-03 12:05:00',13.85,3.23);
INSERT INTO pizza VALUES (NULL,@ord5,'XLarge','Gluten-Free','completed','2025-01-02 17:30:00',28.70,7.84);
INSERT INTO pizza VALUES (NULL,@ord4,'XLarge','Original','completed','2025-02-20 19:11:00',26.75,5.55);
INSERT INTO pizza VALUES (NULL,@ord4,'XLarge','Original','completed','2025-02-20 19:11:00',27.94,5.59);
INSERT INTO pizza VALUES (NULL,@ord4,'XLarge','Original','completed','2025-02-20 19:11:00',31.50,6.25);

-- capture pizza ids
SELECT @p1 := MIN(pizza_PizzaID) FROM pizza WHERE ordertable_OrderID=@ord3;
SELECT @p2 := @p1 + 1;
SELECT @p3 := @p1 + 2;
SELECT @p4 := @p1 + 3;
SELECT @p5 := @p1 + 4;
SELECT @p6 := @p1 + 5;
SELECT @p7 := (SELECT pizza_PizzaID FROM pizza WHERE ordertable_OrderID=@ord6 LIMIT 1);
SELECT @p8 := (SELECT pizza_PizzaID FROM pizza WHERE ordertable_OrderID=@ord1 LIMIT 1);
SELECT @p9 := (SELECT MIN(pizza_PizzaID) FROM pizza WHERE ordertable_OrderID=@ord7);
SELECT @p10 := @p9 + 1;
SELECT @p11 := (SELECT MIN(pizza_PizzaID) FROM pizza WHERE ordertable_OrderID=@ord2);
SELECT @p12 := @p11 + 1;
SELECT @p13 := (SELECT pizza_PizzaID FROM pizza WHERE ordertable_OrderID=@ord5 LIMIT 1);
SELECT @p14 := (SELECT MIN(pizza_PizzaID) FROM pizza WHERE ordertable_OrderID=@ord4);
SELECT @p15 := @p14 + 1;
SELECT @p16 := @p14 + 2;

-- discounts: order- and pizza-level
INSERT INTO pizza_discount SELECT @p8, discount_DiscountID FROM discount WHERE discount_DiscountName='Lunch Special Large';
INSERT INTO order_discount SELECT @ord2, discount_DiscountID FROM discount WHERE discount_DiscountName='Lunch Special Medium';
INSERT INTO pizza_discount SELECT @p12, discount_DiscountID FROM discount WHERE discount_DiscountName='Specialty Pizza';
INSERT INTO order_discount SELECT @ord4, discount_DiscountID FROM discount WHERE discount_DiscountName='Gameday Special';
INSERT INTO pizza_discount SELECT @p13, discount_DiscountID FROM discount WHERE discount_DiscountName='Specialty Pizza';
INSERT INTO order_discount SELECT @ord7, discount_DiscountID FROM discount WHERE discount_DiscountName='Employee';
INSERT INTO pizza_discount SELECT @p16, discount_DiscountID FROM discount WHERE discount_DiscountName='Specialty Pizza';

-- toppings (originals)
INSERT INTO pizza_topping VALUES (@p1,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p2,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p3,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p4,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p5,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p6,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p7,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p8,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p9,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);
INSERT INTO pizza_topping VALUES (@p10,(SELECT topping_TopID FROM topping WHERE topping_TopName='Regular Cheese'),0);

INSERT INTO pizza_topping VALUES (@p1,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p2,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p3,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p4,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p5,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p6,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p7,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p8,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p9,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);
INSERT INTO pizza_topping VALUES (@p10,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pepperoni'),0);

INSERT INTO pizza_topping VALUES (@p11,(SELECT topping_TopID FROM topping WHERE topping_TopName='Four Cheese Blend'),0);
INSERT INTO pizza_topping VALUES (@p12,(SELECT topping_TopID FROM topping WHERE topping_TopName='Four Cheese Blend'),0);
INSERT INTO pizza_topping VALUES (@p13,(SELECT topping_TopID FROM topping WHERE topping_TopName='Four Cheese Blend'),0);
INSERT INTO pizza_topping VALUES (@p14,(SELECT topping_TopID FROM topping WHERE topping_TopName='Four Cheese Blend'),0);
INSERT INTO pizza_topping VALUES (@p15,(SELECT topping_TopID FROM topping WHERE topping_TopName='Four Cheese Blend'),0);
INSERT INTO pizza_topping VALUES (@p16,(SELECT topping_TopID FROM topping WHERE topping_TopName='Four Cheese Blend'),0);
INSERT INTO pizza_topping VALUES (@p7,(SELECT topping_TopID FROM topping WHERE topping_TopName='Four Cheese Blend'),0);

INSERT INTO pizza_topping VALUES (@p14,(SELECT topping_TopID FROM topping WHERE topping_TopName='Chicken'),0);
INSERT INTO pizza_topping VALUES (@p15,(SELECT topping_TopID FROM topping WHERE topping_TopName='Chicken'),0);
INSERT INTO pizza_topping VALUES (@p16,(SELECT topping_TopID FROM topping WHERE topping_TopName='Chicken'),0);

INSERT INTO pizza_topping VALUES (@p11,(SELECT topping_TopID FROM topping WHERE topping_TopName='Mushrooms'),0);
INSERT INTO pizza_topping VALUES (@p12,(SELECT topping_TopID FROM topping WHERE topping_TopName='Mushrooms'),0);
INSERT INTO pizza_topping VALUES (@p13,(SELECT topping_TopID FROM topping WHERE topping_TopName='Mushrooms'),0);

INSERT INTO pizza_topping VALUES (@p8,(SELECT topping_TopID FROM topping WHERE topping_TopName='Banana Peppers'),0);
INSERT INTO pizza_topping VALUES (@p9,(SELECT topping_TopID FROM topping WHERE topping_TopName='Banana Peppers'),0);
INSERT INTO pizza_topping VALUES (@p10,(SELECT topping_TopID FROM topping WHERE topping_TopName='Black Olives'),0);
INSERT INTO pizza_topping VALUES (@p11,(SELECT topping_TopID FROM topping WHERE topping_TopName='Black Olives'),0);
INSERT INTO pizza_topping VALUES (@p12,(SELECT topping_TopID FROM topping WHERE topping_TopName='Green Pepper'),0);
INSERT INTO pizza_topping VALUES (@p13,(SELECT topping_TopID FROM topping WHERE topping_TopName='Green Pepper'),0);

INSERT INTO pizza_topping VALUES (@p4,(SELECT topping_TopID FROM topping WHERE topping_TopName='Ham'),0);
INSERT INTO pizza_topping VALUES (@p5,(SELECT topping_TopID FROM topping WHERE topping_TopName='Ham'),0);
INSERT INTO pizza_topping VALUES (@p6,(SELECT topping_TopID FROM topping WHERE topping_TopName='Onion'),0);
INSERT INTO pizza_topping VALUES (@p7,(SELECT topping_TopID FROM topping WHERE topping_TopName='Onion'),0);
INSERT INTO pizza_topping VALUES (@p14,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pineapple'),0);
INSERT INTO pizza_topping VALUES (@p15,(SELECT topping_TopID FROM topping WHERE topping_TopName='Pineapple'),0);

INSERT INTO pizza_topping VALUES (@p1,(SELECT topping_TopID FROM topping WHERE topping_TopName='Roma Tomato'),0);
INSERT INTO pizza_topping VALUES (@p2,(SELECT topping_TopID FROM topping WHERE topping_TopName='Roma Tomato'),0);
INSERT INTO pizza_topping VALUES (@p3,(SELECT topping_TopID FROM topping WHERE topping_TopName='Sausage'),0);
INSERT INTO pizza_topping VALUES (@p16,(SELECT topping_TopID FROM topping WHERE topping_TopName='Sausage'),0);

-- ensure Bacon, Feta Cheese, and Goat Cheese appear once each
INSERT INTO pizza_topping VALUES (@p8 , (SELECT topping_TopID FROM topping WHERE topping_TopName='Bacon'), 0);
INSERT INTO pizza_topping VALUES (@p9 , (SELECT topping_TopID FROM topping WHERE topping_TopName='Feta Cheese'), 0);
INSERT INTO pizza_topping VALUES (@p10, (SELECT topping_TopID FROM topping WHERE topping_TopName='Goat Cheese'), 0);

COMMIT;
