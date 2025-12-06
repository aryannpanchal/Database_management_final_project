-- ============================================================
-- DropTables.sql
-- Purpose: Drop all tables in PizzaDB safely.
-- ============================================================

-- Use the PizzaDB schema
USE `PizzaDB`;

-- Temporarily disable foreign key checks so we can drop tables
-- in any order without violating constraints.
SET @@SESSION.FOREIGN_KEY_CHECKS = 0;

-- Drop child tables first (discount bridges, toppings mapping, etc.)
DROP TABLE IF EXISTS `pizza_discount`;
DROP TABLE IF EXISTS `order_discount`;
DROP TABLE IF EXISTS `pizza_topping`;
DROP TABLE IF EXISTS `dinein`;
DROP TABLE IF EXISTS `pickup`;
DROP TABLE IF EXISTS `delivery`;
DROP TABLE IF EXISTS `pizza`;
DROP TABLE IF EXISTS `ordertable`;
DROP TABLE IF EXISTS `discount`;
DROP TABLE IF EXISTS `topping`;
DROP TABLE IF EXISTS `customer`;
DROP TABLE IF EXISTS `baseprice`;

-- Re-enable foreign key checks
SET @@SESSION.FOREIGN_KEY_CHECKS = 1;
