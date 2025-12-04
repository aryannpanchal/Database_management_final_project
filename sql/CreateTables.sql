-- ==========================
-- CreateTables.sql (FINAL)
-- ==========================
-- Student Name: Aryan Panchal

CREATE DATABASE IF NOT EXISTS PizzaDB;
USE PizzaDB;

-- CUSTOMER
CREATE TABLE customer (
	customer_CustID INT AUTO_INCREMENT PRIMARY KEY,
	customer_FName VARCHAR(30) NOT NULL,
	customer_LName VARCHAR(30) NOT NULL,
	customer_PhoneNum VARCHAR(30) NOT NULL
);

-- BASEPRICE
CREATE TABLE baseprice (
	baseprice_Size VARCHAR(30) NOT NULL,
	baseprice_CrustType VARCHAR(30) NOT NULL,
	baseprice_CustPrice DECIMAL(10,2) NOT NULL,
	baseprice_BusPrice DECIMAL(10,2) NOT NULL,
	PRIMARY KEY (baseprice_Size, baseprice_CrustType)
);

-- ORDERTABLE
CREATE TABLE ordertable (
	ordertable_OrderID INT AUTO_INCREMENT PRIMARY KEY,
	ordertable_OrderType VARCHAR(30) NOT NULL,
	ordertable_OrderDateTime DATETIME NOT NULL,
	ordertable_CustPrice DECIMAL(10,2) NOT NULL,
	ordertable_BusPrice DECIMAL(10,2) NOT NULL,
	ordertable_IsComplete TINYINT(1) NULL DEFAULT 0,
	customer_CustID INT NULL,
	CONSTRAINT fk_ot_customer FOREIGN KEY (customer_CustID) REFERENCES customer(customer_CustID)
);

-- DINEIN
CREATE TABLE dinein (
	ordertable_OrderID INT PRIMARY KEY,
	dinein_TableNum INT NOT NULL,
	CONSTRAINT fk_dinein_order FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE
);

-- PICKUP
CREATE TABLE pickup (
	ordertable_OrderID INT PRIMARY KEY,
	pickup_IsPickedUp TINYINT(1) NOT NULL DEFAULT 0,
	CONSTRAINT fk_pickup_order FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE
);

-- DELIVERY
CREATE TABLE delivery (
	ordertable_OrderID INT PRIMARY KEY,
	delivery_HouseNum INT NOT NULL,
	delivery_Street VARCHAR(30) NOT NULL,
	delivery_City VARCHAR(30) NOT NULL,
	delivery_State VARCHAR(2) NOT NULL,
	delivery_Zip INT NOT NULL,
	delivery_IsDelivered TINYINT(1) NOT NULL DEFAULT 0,
	CONSTRAINT fk_delivery_order FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE
);

-- TOPPING
CREATE TABLE topping (
	topping_TopID INT AUTO_INCREMENT PRIMARY KEY,
	topping_TopName VARCHAR(30) NOT NULL,
	topping_SmallAMT DECIMAL(10,2) NOT NULL,
	topping_MedAMT DECIMAL(10,2) NOT NULL,
	topping_LgAMT DECIMAL(10,2) NOT NULL,
	topping_XLAMT DECIMAL(10,2) NOT NULL,
	topping_CustPrice DECIMAL(10,2) NOT NULL,
	topping_BusPrice DECIMAL(10,2) NOT NULL,
	topping_MinINVT INT NOT NULL,
	topping_CurINVT INT NOT NULL
);

-- PIZZA
CREATE TABLE pizza (
	pizza_PizzaID INT AUTO_INCREMENT PRIMARY KEY,
	ordertable_OrderID INT NOT NULL,
	pizza_Size VARCHAR(30) NOT NULL,
	pizza_CrustType VARCHAR(30) NOT NULL,
	pizza_PizzaState VARCHAR(20) NOT NULL,
	pizza_PizzaDate DATETIME NOT NULL,
	pizza_CustPrice DECIMAL(10,2) NOT NULL,
	pizza_BusPrice DECIMAL(10,2) NOT NULL,
	CONSTRAINT fk_pizza_order FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE,
	CONSTRAINT fk_pizza_baseprice FOREIGN KEY (pizza_Size, pizza_CrustType) REFERENCES baseprice(baseprice_Size, baseprice_CrustType)
);

-- PIZZA_TOPPING
CREATE TABLE pizza_topping (
	pizza_PizzaID INT NOT NULL,
	topping_TopID INT NOT NULL,
	pizza_topping_IsDouble INT NOT NULL,
	PRIMARY KEY (pizza_PizzaID, topping_TopID),
	CONSTRAINT fk_pt_pizza FOREIGN KEY (pizza_PizzaID) REFERENCES pizza(pizza_PizzaID) ON DELETE CASCADE,
	CONSTRAINT fk_pt_topping FOREIGN KEY (topping_TopID) REFERENCES topping(topping_TopID)
);

-- DISCOUNT
CREATE TABLE discount (
	discount_DiscountID INT AUTO_INCREMENT PRIMARY KEY,
	discount_DiscountName VARCHAR(30) NOT NULL,
	discount_Amount DECIMAL(10,2) NOT NULL,
	discount_IsPercent TINYINT(1) NOT NULL
);

-- PIZZA_DISCOUNT
CREATE TABLE pizza_discount (
	pizza_PizzaID INT NOT NULL,
	discount_DiscountID INT NOT NULL,
	PRIMARY KEY (pizza_PizzaID, discount_DiscountID),
	CONSTRAINT fk_pd_pizza FOREIGN KEY (pizza_PizzaID) REFERENCES pizza(pizza_PizzaID) ON DELETE CASCADE,
	CONSTRAINT fk_pd_discount FOREIGN KEY (discount_DiscountID) REFERENCES discount(discount_DiscountID)
);

-- ORDER_DISCOUNT
CREATE TABLE order_discount (
	ordertable_OrderID INT NOT NULL,
	discount_DiscountID INT NOT NULL,
	PRIMARY KEY (ordertable_OrderID, discount_DiscountID),
	CONSTRAINT fk_od_order FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID) ON DELETE CASCADE,
	CONSTRAINT fk_od_discount FOREIGN KEY (discount_DiscountID) REFERENCES discount(discount_DiscountID)
);
