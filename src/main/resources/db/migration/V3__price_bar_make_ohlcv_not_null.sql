-- If any rows already exist, set safe defaults or delete them before running this.
-- Here we default open/high/low to close when null, and volume to 0.

UPDATE price_bar SET open = close WHERE open IS NULL;
UPDATE price_bar SET high = close WHERE high IS NULL;
UPDATE price_bar SET low  = close WHERE low  IS NULL;
UPDATE price_bar SET volume = 0 WHERE volume IS NULL;

ALTER TABLE price_bar ALTER COLUMN open SET NOT NULL;
ALTER TABLE price_bar ALTER COLUMN high SET NOT NULL;
ALTER TABLE price_bar ALTER COLUMN low  SET NOT NULL;
ALTER TABLE price_bar ALTER COLUMN volume SET NOT NULL;

ALTER TABLE price_bar
ADD CONSTRAINT chk_price_bar_nonnegative
CHECK (open >= 0 AND high >= 0 AND low >= 0 AND close >= 0 AND volume >= 0);