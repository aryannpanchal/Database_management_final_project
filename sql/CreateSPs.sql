-- ============================================================
-- CreateSPs.sql
-- Purpose: Define stored procedures and triggers for PizzaDB.
-- ============================================================

USE PizzaDB;

-- ------------------------------------------------------------
-- Procedure: AddCustomerIfNotExists
-- Inputs:
--   fName, lName, phone  - customer identity and phone number
-- Behavior:
--   1. Looks up an existing customer with the same name + phone.
--   2. If not found, inserts a new customer.
--   3. Returns the customer ID (existing or newly created).
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddCustomerIfNotExists (
    IN fName VARCHAR(30),
    IN lName VARCHAR(30),
    IN phone VARCHAR(30)
)
BEGIN
    -- Variable to hold matching customer ID (if any)
    DECLARE custID INT;

    -- Try to find an existing customer with the same name + phone
    SELECT customer_CustID INTO custID
    FROM customer
    WHERE customer_FName = fName
      AND customer_LName = lName
      AND customer_PhoneNum = phone;

    -- If no such customer exists, insert a new one
    IF custID IS NULL THEN
        INSERT INTO customer (customer_FName, customer_LName, customer_PhoneNum)
        VALUES (fName, lName, phone);

        -- Capture the ID of the newly inserted customer
        SET custID = LAST_INSERT_ID();
    END IF;

    -- Return the customer ID (either existing or new)
    SELECT custID AS NewCustomerID;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: CreateOrder
-- Inputs:
--   custID       - customer_CustID; may be NULL for walk-in
--   orderType    - 'Dine-in', 'Pickup', or 'Delivery'
--   orderDateTime- timestamp of order
--   custPrice    - total price to customer (initial value)
--   busPrice     - total business cost (initial value)
--   isComplete   - flag 0/1
--   tableNum     - dine-in table number (if dine-in)
--   houseNum     - house number (if delivery)
--   street       - street string (if delivery)
--   zip          - zip code (string) (if delivery)
--   isDelivered  - delivery flag (if delivery)
--   isPickedUp   - pickup flag (if pickup)
-- Behavior:
--   1. Inserts a row into ordertable.
--   2. Based on orderType, inserts into dinein/pickup/delivery.
--   3. Returns the new orderID.
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE CreateOrder (
    IN custID INT,
    IN orderType VARCHAR(30),
    IN orderDateTime DATETIME,
    IN custPrice DECIMAL(6,2),
    IN busPrice DECIMAL(6,2),
    IN isComplete BOOLEAN,
    IN tableNum INT,
    IN houseNum INT,
    IN street VARCHAR(40),
    IN zip VARCHAR(10),
    IN isDelivered BOOLEAN,
    IN isPickedUp BOOLEAN
)
BEGIN
    -- Local variable to hold the new order ID
    DECLARE orderID INT;

    -- Insert order header into main ordertable
    INSERT INTO ordertable (
        customer_CustID,
        ordertable_OrderType,
        ordertable_OrderDateTime,
        ordertable_CustPrice,
        ordertable_BusPrice,
        ordertable_isComplete
    )
    VALUES (
        custID,
        orderType,
        orderDateTime,
        custPrice,
        busPrice,
        isComplete
    );

    -- Capture the auto-generated order ID
    SET orderID = LAST_INSERT_ID();

    -- If this is a dine-in order, create dinein child row
    IF orderType = 'Dine-in' THEN
        INSERT INTO dinein (ordertable_OrderID, dinein_TableNum)
        VALUES (orderID, tableNum);
    END IF;

    -- If this is a pickup order, create pickup child row
    IF orderType = 'Pickup' THEN
        INSERT INTO pickup (ordertable_OrderID, pickup_IsPickedUp)
        VALUES (orderID, isPickedUp);
    END IF;

    -- If this is a delivery order, create delivery child row
    IF orderType = 'Delivery' THEN
        INSERT INTO delivery (
            ordertable_OrderID,
            delivery_HouseNum,
            delivery_Street,
            delivery_Zip,
            delivery_IsDelivered
        )
        VALUES (
            orderID,
            houseNum,
            street,
            zip,
            isDelivered
        );
    END IF;

    -- Return the order ID to the caller
    SELECT orderID AS NewOrderID;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: AddPizza
-- Inputs:
--   orderID    - the order this pizza belongs to
--   size       - pizza size
--   crust      - pizza crust type
--   pizzaDate  - timestamp of pizza
--   custPrice  - customer price for this pizza
--   busPrice   - business cost for this pizza
-- Behavior:
--   1. Inserts a pizza row tied to a specific order.
--   2. Returns the new pizza ID.
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddPizza (
    IN orderID INT,
    IN size VARCHAR(30),
    IN crust VARCHAR(30),
    IN pizzaDate DATETIME,
    IN custPrice DECIMAL(6,2),
    IN busPrice DECIMAL(6,2)
)
BEGIN
    -- Insert the new pizza row
    INSERT INTO pizza (
        ordertable_OrderID,
        pizza_Size,
        pizza_CrustType,
        pizza_PizzaDate,
        pizza_CustPrice,
        pizza_BusPrice
    )
    VALUES (
        orderID,
        size,
        crust,
        pizzaDate,
        custPrice,
        busPrice
    );

    -- Return ID of inserted pizza
    SELECT LAST_INSERT_ID() AS NewPizzaID;
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: AddPizzaTopping
-- Inputs:
--   pizzaID   - target pizza
--   toppingID - topping to add
--   isDouble  - 0/1 flag (single vs double topping)
-- Behavior:
--   Creates a record in pizza_topping linking pizza & topping.
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddPizzaTopping (
    IN pizzaID INT,
    IN toppingID INT,
    IN isDouble BOOLEAN
)
BEGIN
    INSERT INTO pizza_topping (
        pizza_PizzaID,
        topping_TopID,
        pizza_topping_isDouble
    )
    VALUES (
        pizzaID,
        toppingID,
        isDouble
    );
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: AddDiscountToOrder
-- Inputs:
--   orderID    - order receiving the discount
--   discountID - discount to apply
-- Behavior:
--   Simple insert into order_discount bridge table.
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddDiscountToOrder (
    IN orderID INT,
    IN discountID INT
)
BEGIN
    INSERT INTO order_discount (ordertable_OrderID, discount_DiscountID)
    VALUES (orderID, discountID);
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Procedure: AddDiscountToPizza
-- Inputs:
--   pizzaID    - pizza receiving the discount
--   discountID - discount to apply
-- Behavior:
--   Simple insert into pizza_discount bridge table.
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE AddDiscountToPizza (
    IN pizzaID INT,
    IN discountID INT
)
BEGIN
    INSERT INTO pizza_discount (pizza_PizzaID, discount_DiscountID)
    VALUES (pizzaID, discountID);
END //
DELIMITER ;

-- ------------------------------------------------------------
-- Trigger: UpdateOrderPrice_AfterPizzaInsert
-- Timing: AFTER INSERT on pizza
-- Behavior:
--   Whenever a new pizza is inserted, this trigger automatically
--   increments the order's total customer price and business cost
--   by the values from the newly inserted pizza row.
--   This keeps ordertable_CustPrice and ordertable_BusPrice in sync
--   with its pizzas (at least at insert time).
-- ------------------------------------------------------------
DELIMITER //
CREATE TRIGGER UpdateOrderPrice_AfterPizzaInsert
AFTER INSERT ON pizza
FOR EACH ROW
BEGIN
    UPDATE ordertable
    SET ordertable_CustPrice = IFNULL(ordertable_CustPrice, 0) + NEW.pizza_CustPrice,
        ordertable_BusPrice = IFNULL(ordertable_BusPrice, 0) + NEW.pizza_BusPrice
    WHERE ordertable_OrderID = NEW.ordertable_OrderID;
END //
DELIMITER ;
