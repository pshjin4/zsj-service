-- DESTRUCTIVE, ONE-TIME migration: removes all current recipes and their related records.
-- Run only because existing recipe data is confirmed to be test data.
DELETE FROM cooking_records;
DELETE FROM recipe_favorites;
DELETE FROM recipe_ingredients;
DELETE FROM recipe_steps;
DELETE FROM recipe_images;
DELETE FROM recipes;

ALTER TABLE recipe_categories
  DROP INDEX uk_category_name,
  ADD COLUMN user_openid VARCHAR(128) NULL AFTER name,
  ADD UNIQUE KEY uk_category_user_name (user_openid, name);

UPDATE recipe_categories SET name = '荤菜', sort_order = 1 WHERE name = '家常菜';
UPDATE recipe_categories SET name = '素菜', sort_order = 2 WHERE name = '快手菜';
UPDATE recipe_categories SET name = '荤素搭配', sort_order = 3 WHERE name = '汤羹';
UPDATE recipe_categories SET name = '小吃', sort_order = 4 WHERE name = '烘焙';

INSERT INTO recipe_categories (name, user_openid, sort_order)
SELECT '荤菜', NULL, 1 WHERE NOT EXISTS (SELECT 1 FROM recipe_categories WHERE name = '荤菜' AND user_openid IS NULL);
INSERT INTO recipe_categories (name, user_openid, sort_order)
SELECT '素菜', NULL, 2 WHERE NOT EXISTS (SELECT 1 FROM recipe_categories WHERE name = '素菜' AND user_openid IS NULL);
INSERT INTO recipe_categories (name, user_openid, sort_order)
SELECT '荤素搭配', NULL, 3 WHERE NOT EXISTS (SELECT 1 FROM recipe_categories WHERE name = '荤素搭配' AND user_openid IS NULL);
INSERT INTO recipe_categories (name, user_openid, sort_order)
SELECT '小吃', NULL, 4 WHERE NOT EXISTS (SELECT 1 FROM recipe_categories WHERE name = '小吃' AND user_openid IS NULL);
