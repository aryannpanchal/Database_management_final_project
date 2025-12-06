-- ============================================================
-- CreateTables.sql
-- Purpose: Define the PizzaDB schema (all base tables).
-- ============================================================

-- Create the database that will hold all pizza-related tables
CREATE DATABASE PizzaDB;

-- Switch the current database context to PizzaDB
USE PizzaDB;

-- ============================================================
-- Table: baseprice
-- Stores base customer price and business cost
-- for each (size, crust) combination.
-- ============================================================
CREATE TABLE baseprice (
    baseprice_Size       VARCHAR(30),
    baseprice_CrustType  VARCHAR(30),
    baseprice_CustPrice  DECIMAL(5,2) NOT NULL,
    baseprice_BusPrice   DECIMAL(5,2) NOT NULL,

    PRIMARY KEY (baseprice_Size, baseprice_CrustType)
);

-- ============================================================
-- Table: customer
-- Basic customer identity and phone contact.
-- ============================================================
CREATE TABLE customer (
    customer_CustID   INT AUTO_INCREMENT PRIMARY KEY,
    customer_FName    VARCHAR(30) NOT NULL,
    customer_LName    VARCHAR(30) NOT NULL,
    customer_PhoneNum VARCHAR(30) NOT NULL
);

-- ============================================================
-- Table: ordertable
-- Order header. One row per order.
-- ordertable_OrderType determines child table:
--   'dinein', 'pickup', or 'delivery'.
-- ============================================================
CREATE TABLE ordertable (
    ordertable_OrderID      INT AUTO_INCREMENT PRIMARY KEY,
    customer_CustID         INT,
    ordertable_OrderType    VARCHAR(30) NOT NULL,
    ordertable_OrderDateTime DATETIME   NOT NULL,
    ordertable_CustPrice    DECIMAL(5,2) NOT NULL,
    ordertable_BusPrice     DECIMAL(5,2) NOT NULL,
    ordertable_IsComplete   BOOLEAN      DEFAULT 0,

    FOREIGN KEY (customer_CustID)
        REFERENCES customer (customer_CustID)
);

-- ============================================================
-- Table: pizza
-- Individual pizzas belonging to an order.
-- Linked to baseprice via (pizza_Size, pizza_CrustType).
-- ============================================================
CREATE TABLE pizza (
    pizza_PizzaID      INT AUTO_INCREMENT PRIMARY KEY,
    pizza_Size         VARCHAR(30),
    pizza_CrustType    VARCHAR(30),
    ordertable_OrderID INT,
    pizza_PizzaState   VARCHAR(30),
    pizza_PizzaDate    DATETIME,
    pizza_CustPrice    DECIMAL(5,2),
    pizza_BusPrice     DECIMAL(5,2),

    FOREIGN KEY (ordertable_OrderID)
        REFERENCES ordertable (ordertable_OrderID),

    FOREIGN KEY (pizza_Size, pizza_CrustType)
        REFERENCES baseprice (baseprice_Size, baseprice_CrustType)
);

-- ============================================================
-- Table: topping
-- Master list of toppings, per-size default amounts,
-- prices, and inventory.
-- ============================================================
CREATE TABLE topping (
    topping_TopID      INT AUTO_INCREMENT PRIMARY KEY,
    topping_TopName    VARCHAR(30) NOT NULL,
    topping_SmallAMT   DECIMAL(5,2) NOT NULL,
    topping_MedAMT     DECIMAL(5,2) NOT NULL,
    topping_LgAMT      DECIMAL(5,2) NOT NULL,
    topping_XLAMT      DECIMAL(5,2) NOT NULL,
    topping_CustPrice  DECIMAL(5,2) NOT NULL,
    topping_BusPrice   DECIMAL(5,2) NOT NULL,
    topping_MinINVT    INT          NOT NULL,
    topping_CurINVT    INT          NOT NULL
);

-- ============================================================
-- Table: pizza_topping
-- Bridge between pizza and topping.
-- Tracks whether topping is doubled.
-- ============================================================
CREATE TABLE pizza_topping (
    pizza_PizzaID         INT NOT NULL,
    topping_TopID         INT NOT NULL,
    pizza_topping_IsDouble INT NOT NULL,

    PRIMARY KEY (pizza_PizzaID, topping_TopID),

    FOREIGN KEY (pizza_PizzaID)
        REFERENCES pizza (pizza_PizzaID),

    FOREIGN KEY (topping_TopID)
        REFERENCES topping (topping_TopID)
);

-- ============================================================
-- Table: discount
-- All discount definitions (fixed or percent).
-- ============================================================
CREATE TABLE discount (
    discount_DiscountID   INT AUTO_INCREMENT PRIMARY KEY,
    discount_DiscountName VARCHAR(30) NOT NULL,
    discount_Amount       DECIMAL(5,2) NOT NULL,
    discount_IsPercent    BOOLEAN     NOT NULL
);

-- ============================================================
-- Table: pizza_discount
-- Discounts applied to individual pizzas.
-- ============================================================
CREATE TABLE pizza_discount (
    pizza_PizzaID      INT,
    discount_DiscountID INT,

    PRIMARY KEY (pizza_PizzaID, discount_DiscountID),

    FOREIGN KEY (pizza_PizzaID)
        REFERENCES pizza (pizza_PizzaID),

    FOREIGN KEY (discount_DiscountID)
        REFERENCES discount (discount_DiscountID)
);

-- ============================================================
-- Table: order_discount
-- Discounts applied at the order level.
-- ============================================================
CREATE TABLE order_discount (
    ordertable_OrderID  INT,
    discount_DiscountID INT,

    PRIMARY KEY (ordertable_OrderID, discount_DiscountID),

    FOREIGN KEY (ordertable_OrderID)
        REFERENCES ordertable (ordertable_OrderID),

    FOREIGN KEY (discount_DiscountID)
        REFERENCES discount (discount_DiscountID)
);

-- ============================================================
-- Table: pickup
-- Extra data for pickup orders (1:1 with ordertable).
-- ============================================================
CREATE TABLE pickup (
    ordertable_OrderID INT PRIMARY KEY,
    pickup_IsPickedUp  BOOLEAN NOT NULL DEFAULT 0,

    FOREIGN KEY (ordertable_OrderID)
        REFERENCES ordertable (ordertable_OrderID)
);

-- ============================================================
-- Table: delivery
-- Extra data for delivery orders (1:1 with ordertable).
-- Stores address and delivery status.
-- ============================================================
CREATE TABLE delivery (
    ordertable_OrderID   INT PRIMARY KEY,
    delivery_HouseNum    INT         NOT NULL,
    delivery_Street      VARCHAR(30) NOT NULL,
    delivery_City        VARCHAR(30) NOT NULL,
    delivery_State       VARCHAR(2)  NOT NULL,
    delivery_Zip         INT         NOT NULL,
    delivery_IsDelivered BOOLEAN     NOT NULL DEFAULT 0,

    FOREIGN KEY (ordertable_OrderID)
        REFERENCES ordertable (ordertable_OrderID)
);

-- ============================================================
-- Table: dinein
-- Extra data for dine-in orders (1:1 with ordertable).
-- Tracks table number.
-- ============================================================
CREATE TABLE dinein (
    ordertable_OrderID INT PRIMARY KEY,
    dinein_TableNum    INT NOT NULL,

    FOREIGN KEY (ordertable_OrderID)
        REFERENCES ordertable (ordertable_OrderID)
);

