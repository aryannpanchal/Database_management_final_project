-- ============================================================
-- CreateTables.sql
-- Purpose: Define the PizzaDB schema (all base tables).
-- ============================================================

-- Create the database that will hold all pizza-related tables
CREATE DATABASE PizzaDB;

-- Switch the current database context to PizzaDB
USE PizzaDB;

-- ============================================
-- Table: baseprice
-- Description:
--   Stores the base price and business cost for a pizza
--   for each combination of size and crust type.
--   This is the "starting point" before toppings/discounts.
-- ============================================
CREATE TABLE baseprice (
    -- Logical pizza size (Small, Medium, Large, XLarge, etc.)
    baseprice_Size VARCHAR(30),

    -- Type of crust (Thin, Original, Pan, Gluten-Free, etc.)
    baseprice_CrustType VARCHAR(30),

    -- Price charged to the customer for this base pizza
    baseprice_CustPrice DECIMAL(5,2) NOT NULL,

    -- Internal business cost to make this base pizza
    baseprice_BusPrice DECIMAL(5,2) NOT NULL,

    -- Composite primary key:
    -- Each (size, crust type) pair is unique
    PRIMARY KEY (baseprice_Size, baseprice_CrustType)
);

-- ============================================
-- Table: customer
-- Description:
--   Stores customer identity and contact information.
-- ============================================
CREATE TABLE customer (
    -- Surrogate primary key, auto-incremented
    customer_CustID INT AUTO_INCREMENT PRIMARY KEY,

    -- Customer first name
    customer_FName VARCHAR(30) NOT NULL,

    -- Customer last name
    customer_LName VARCHAR(30) NOT NULL,

    -- Customer phone number (string to allow dashes, etc.)
    customer_PhoneNum VARCHAR(30) NOT NULL
);

-- ============================================
-- Table: ordertable
-- Description:
--   Top-level order header row. One row per order.
--   Order type (dine-in, pickup, delivery) determines
--   which child table (dinein/pickup/delivery) will be used.
-- ============================================
CREATE TABLE ordertable (
    -- Unique order ID
    ordertable_OrderID INT AUTO_INCREMENT PRIMARY KEY,

    -- Foreign key to the customer who placed the order
    customer_CustID INT,

    -- Order type: 'dinein', 'pickup', or 'delivery'
    ordertable_OrderType VARCHAR(30) NOT NULL,

    -- Date and time when the order was placed
    ordertable_OrderDateTime DATETIME NOT NULL,

    -- Total price charged to the customer for this order
    ordertable_CustPrice DECIMAL(5,2) NOT NULL,

    -- Total internal business cost for this order
    ordertable_BusPrice DECIMAL(5,2) NOT NULL,

    -- Flag indicating whether the order is complete
    ordertable_IsComplete BOOLEAN DEFAULT 0,

    -- Link to customer table
    FOREIGN KEY (customer_CustID) REFERENCES customer(customer_CustID)
);

-- ============================================
-- Table: pizza
-- Description:
--   Represents an individual pizza that belongs to an order.
--   Linked to base price via (size, crust type).
-- ============================================
CREATE TABLE pizza (
    -- Unique pizza ID
    pizza_PizzaID INT AUTO_INCREMENT PRIMARY KEY,

    -- Pizza size (matches baseprice.baseprice_Size)
    pizza_Size VARCHAR(30),

    -- Pizza crust type (matches baseprice.baseprice_CrustType)
    pizza_CrustType VARCHAR(30),

    -- The order this pizza belongs to
    ordertable_OrderID INT,

    -- Pizza state (e.g., 'completed')
    pizza_PizzaState VARCHAR(30),

    -- Timestamp for when the pizza was made/recorded
    pizza_PizzaDate DATETIME,

    -- Customer price for this pizza (including toppings, before discounts)
    pizza_CustPrice DECIMAL(5,2),

    -- Business cost for this pizza (including toppings)
    pizza_BusPrice DECIMAL(5,2),

    -- FK link to owning order
    FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID),

    -- FK link to baseprice table via (size, crust)
    FOREIGN KEY (pizza_Size, pizza_CrustType) REFERENCES baseprice(baseprice_Size, baseprice_CrustType)
);

-- ============================================
-- Table: topping
-- Description:
--   Master list of all toppings, their default amounts per size,
--   and pricing/business cost details plus inventory tracking.
-- ============================================
CREATE TABLE topping (
    -- Unique topping ID
    topping_TopID INT AUTO_INCREMENT PRIMARY KEY,

    -- Human-readable topping name (Pepperoni, Sausage, etc.)
    topping_TopName VARCHAR(30) NOT NULL,

    -- Default amount used on a Small pizza
    topping_SmallAMT DECIMAL(5,2) NOT NULL,

    -- Default amount used on a Medium pizza
    topping_MedAMT DECIMAL(5,2) NOT NULL,

    -- Default amount used on a Large pizza
    topping_LgAMT DECIMAL(5,2) NOT NULL,

    -- Default amount used on an XLarge pizza
    topping_XLAMT DECIMAL(5,2) NOT NULL,

    -- Price charged per topping on a pizza
    topping_CustPrice DECIMAL(5,2) NOT NULL,

    -- Internal business cost per topping quantity
    topping_BusPrice DECIMAL(5,2) NOT NULL,

    -- Minimum inventory threshold (for reordering)
    topping_MinINVT INT NOT NULL,

    -- Current inventory count
    topping_CurINVT INT NOT NULL
);

-- ============================================
-- Table: pizza_topping
-- Description:
--   Bridge table between pizza and topping.
--   Also tracks whether the topping is a double portion.
-- ============================================
CREATE TABLE pizza_topping (
    -- The pizza this topping belongs to
    pizza_PizzaID INT NOT NULL,

    -- The topping used on this pizza
    topping_TopID INT NOT NULL,

    -- Flag: 1 = double topping, 0 = single topping
    pizza_topping_IsDouble INT NOT NULL,

    -- Composite PK ensures a topping appears at most once per pizza
    PRIMARY KEY (pizza_PizzaID, topping_TopID),

    -- FK to pizza
    FOREIGN KEY (pizza_PizzaID) REFERENCES pizza(pizza_PizzaID),

    -- FK to topping
    FOREIGN KEY (topping_TopID) REFERENCES topping(topping_TopID)
);

-- ============================================
-- Table: discount
-- Description:
--   Lookup table for all discounts (fixed-dollar or percentage).
-- ============================================
CREATE TABLE discount (
    -- Unique discount ID
    discount_DiscountID INT AUTO_INCREMENT PRIMARY KEY,

    -- Name of discount (Employee, Gameday Special, etc.)
    discount_DiscountName VARCHAR(30) NOT NULL,

    -- Discount amount:
    --   If discount_IsPercent = 0, then this is a flat dollar amount.
    --   If discount_IsPercent = 1, then this is a percentage (0-100).
    discount_Amount DECIMAL(5,2) NOT NULL,

    -- 0 = dollar amount, 1 = percentage
    discount_IsPercent BOOLEAN NOT NULL
);

-- ============================================
-- Table: pizza_discount
-- Description:
--   Many-to-many relationship between pizza and discount.
--   Use this when discounts apply to individual pizzas.
-- ============================================
CREATE TABLE pizza_discount (
    -- Pizza that receives a discount
    pizza_PizzaID INT,

    -- Discount applied to that pizza
    discount_DiscountID INT,

    -- Composite PK: one row per (pizza, discount) combination
    PRIMARY KEY (pizza_PizzaID, discount_DiscountID),

    -- FK to pizza
    FOREIGN KEY (pizza_PizzaID) REFERENCES pizza(pizza_PizzaID),

    -- FK to discount
    FOREIGN KEY (discount_DiscountID) REFERENCES discount(discount_DiscountID)
);

-- ============================================
-- Table: order_discount
-- Description:
--   Many-to-many relationship between order and discount.
--   Use this when discounts apply to the entire order.
-- ============================================
CREATE TABLE order_discount (
    -- Order that receives a discount
    ordertable_OrderID INT,

    -- Discount applied to the order
    discount_DiscountID INT,

    -- Composite PK: one row per (order, discount)
    PRIMARY KEY (ordertable_OrderID, discount_DiscountID),

    -- FK to order
    FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID),

    -- FK to discount
    FOREIGN KEY (discount_DiscountID) REFERENCES discount(discount_DiscountID)
);

-- ============================================
-- Table: pickup
-- Description:
--   Extra data for pickup orders (one-to-one with ordertable).
-- ============================================
CREATE TABLE pickup (
    -- Primary key and FK to ordertable
    ordertable_OrderID INT PRIMARY KEY,

    -- Flag: has the order been picked up yet?
    pickup_IsPickedUp BOOLEAN NOT NULL DEFAULT 0,

    FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID)
);

-- ============================================
-- Table: delivery
-- Description:
--   Extra data for delivery orders (one-to-one with ordertable).
--   Stores address and delivery status.
-- ============================================
CREATE TABLE delivery (
    -- Primary key and FK to ordertable
    ordertable_OrderID INT PRIMARY KEY,

    -- Address fields for delivery
    delivery_HouseNum INT NOT NULL,
    delivery_Street VARCHAR(30) NOT NULL,
    delivery_City VARCHAR(30) NOT NULL,
    delivery_State VARCHAR(2) NOT NULL,
    delivery_Zip INT NOT NULL,

    -- Flag indicating whether delivery has been completed
    delivery_IsDelivered BOOLEAN NOT NULL DEFAULT 0,

    FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID)
);

-- ============================================
-- Table: dinein
-- Description:
--   Extra data for dine-in orders (one-to-one with ordertable).
--   Mainly tracks table number.
-- ============================================
CREATE TABLE dinein (
    -- Primary key and FK to ordertable
    ordertable_OrderID INT PRIMARY KEY,

    -- Table number where the customer is seated
    dinein_TableNum INT NOT NULL,

    FOREIGN KEY (ordertable_OrderID) REFERENCES ordertable(ordertable_OrderID)
);
