-- 극도로 복잡한 UPDATE 서브쿼리 테스트 쿼리
-- 깊게 중첩된 SELECT 서브쿼리 구조 (최대 5-6단계 중첩)
-- 총 20개 이상의 SELECT 문이 포함됨

UPDATE orders o
SET 
    -- SET 절 1: 4중 중첩 서브쿼리
    total_amount = (
        SELECT COALESCE(
            (
                SELECT SUM(
                    CASE 
                        WHEN EXISTS (
                            SELECT 1 
                            FROM order_item_details oid
                            WHERE oid.item_id = oi.id
                              AND oid.status = 'ACTIVE'
                              AND oid.created_at > (
                                  SELECT MAX(updated_at)
                                  FROM order_item_history
                                  WHERE item_id = oi.id
                              )
                        ) THEN (
                            SELECT price * quantity * (
                                SELECT discount_multiplier
                                FROM discount_rules
                                WHERE rule_id = (
                                    SELECT id
                                    FROM discount_rule_master
                                    WHERE category = (
                                        SELECT category_name
                                        FROM product_categories
                                        WHERE category_id = oi.product_category_id
                                    )
                                )
                            )
                            FROM discount_rules
                            LIMIT 1
                        )
                        ELSE price * quantity
                    END
                )
                FROM order_items oi
                WHERE oi.order_id = o.id
                  AND oi.status IN (
                      SELECT status_code
                      FROM valid_order_statuses
                      WHERE is_active = true
                        AND status_type = (
                            SELECT type_name
                            FROM status_types
                            WHERE type_id = (
                                SELECT status_type_id
                                FROM order_configurations
                                WHERE config_key = 'ORDER_STATUS_TYPE'
                            )
                        )
                  )
            ),
            0
        )
    ),
    
    -- SET 절 2: 5중 중첩 서브쿼리 (CASE 표현식 내부)
    discount_rate = CASE 
        WHEN o.customer_id IN (
            SELECT customer_id 
            FROM vip_customers 
            WHERE membership_level IN (
                SELECT level_code
                FROM membership_levels
                WHERE level_priority >= (
                    SELECT MIN(level_priority)
                    FROM membership_levels
                    WHERE is_premium = true
                      AND region_id = (
                          SELECT region_id
                          FROM customer_regions
                          WHERE customer_id = o.customer_id
                            AND is_primary = (
                                SELECT TRUE
                                FROM customer_region_settings
                                WHERE setting_key = 'PRIMARY_REGION_ENABLED'
                            )
                      )
                )
            )
        ) THEN (
            SELECT premium_discount 
            FROM discount_settings 
            WHERE customer_type = (
                SELECT customer_type_name
                FROM customer_type_mappings
                WHERE customer_id = o.customer_id
                  AND mapping_id = (
                      SELECT id
                      FROM type_mapping_configs
                      WHERE config_type = 'VIP'
                        AND is_active = (
                            SELECT TRUE
                            FROM system_flags
                            WHERE flag_name = 'VIP_MAPPING_ENABLED'
                        )
                  )
            )
        )
        ELSE (
            SELECT standard_discount 
            FROM discount_settings 
            WHERE customer_type = 'STANDARD'
              AND region_id = (
                  SELECT region_id
                  FROM customers
                  WHERE id = o.customer_id
                    AND region_id IN (
                        SELECT id
                        FROM regions
                        WHERE country_code = (
                            SELECT country_code
                            FROM country_settings
                            WHERE setting_name = 'DEFAULT_COUNTRY'
                        )
                    )
              )
        )
    END,
    
    -- SET 절 3: 6중 중첩 서브쿼리 (EXISTS 체인)
    last_updated_by = CASE
        WHEN EXISTS (
            SELECT 1 
            FROM audit_log al
            WHERE al.order_id = o.id 
              AND al.action_type IN (
                  SELECT action_code
                  FROM audit_action_types
                  WHERE category = 'UPDATE'
                    AND requires_admin = (
                        SELECT requires_admin_flag
                        FROM audit_configurations
                        WHERE config_id = (
                            SELECT audit_config_id
                            FROM system_audit_settings
                            WHERE setting_name = 'UPDATE_AUDIT_CONFIG'
                              AND is_enabled = (
                                  SELECT TRUE
                                  FROM global_settings
                                  WHERE setting_key = 'AUDIT_ENABLED'
                              )
                        )
                    )
              )
              AND al.created_at > (
                  SELECT MAX(created_at) - INTERVAL '30 days'
                  FROM audit_log
                  WHERE order_id = o.id
                    AND action_type = (
                        SELECT action_code
                        FROM audit_action_types
                        WHERE action_name = 'LAST_UPDATE'
                    )
              )
        ) THEN (
            SELECT admin_id 
            FROM audit_log 
            WHERE order_id = o.id 
              AND admin_id IN (
                  SELECT user_id
                  FROM admin_users
                  WHERE role_id = (
                      SELECT role_id
                      FROM admin_roles
                      WHERE role_name = 'ORDER_ADMIN'
                        AND permission_level >= (
                            SELECT MIN(permission_level)
                            FROM admin_roles
                            WHERE can_update_orders = true
                        )
                  )
              )
            ORDER BY created_at DESC 
            LIMIT 1
        )
        ELSE o.created_by
    END,
    
    -- SET 절 4: 5중 중첩 서브쿼리 (집계 + CASE)
    shipping_cost = (
        SELECT 
            CASE 
                WHEN COUNT(*) > (
                    SELECT threshold_value
                    FROM shipping_thresholds
                    WHERE threshold_type = 'BULK'
                      AND region_id = (
                          SELECT region_id
                          FROM customers
                          WHERE id = o.customer_id
                            AND region_id IN (
                                SELECT id
                                FROM regions
                                WHERE shipping_zone = (
                                    SELECT zone_name
                                    FROM shipping_zones
                                    WHERE zone_id = (
                                        SELECT default_zone_id
                                        FROM shipping_configurations
                                        WHERE config_key = 'DEFAULT_ZONE'
                                    )
                                )
                            )
                      )
                ) THEN (
                    SELECT bulk_shipping_rate 
                    FROM shipping_rates 
                    WHERE region = (
                        SELECT region_name
                        FROM regions
                        WHERE id = (
                            SELECT region_id
                            FROM customers
                            WHERE id = o.customer_id
                        )
                    )
                      AND rate_type = (
                          SELECT rate_type_code
                          FROM shipping_rate_types
                          WHERE is_bulk = true
                            AND priority = (
                                SELECT MAX(priority)
                                FROM shipping_rate_types
                                WHERE is_active = true
                            )
                      )
                )
                ELSE (
                    SELECT base_shipping_rate
                    FROM shipping_rates
                    WHERE region = (
                        SELECT region_name
                        FROM regions
                        WHERE id = (
                            SELECT region_id
                            FROM customers
                            WHERE id = o.customer_id
                        )
                    )
                )
            END
        FROM order_items oi2
        WHERE oi2.order_id = o.id
          AND oi2.product_id IN (
              SELECT product_id
              FROM shippable_products
              WHERE requires_special_shipping = (
                  SELECT FALSE
                  FROM shipping_policies
                  WHERE policy_type = 'STANDARD'
              )
          )
    ),
    
    -- SET 절 5: 4중 중첩 서브쿼리
    tax_amount = (
        SELECT 
            SUM(
                CASE
                    WHEN oi.tax_exempt = (
                        SELECT FALSE
                        FROM tax_exemption_rules
                        WHERE customer_id = o.customer_id
                          AND exemption_type = (
                              SELECT exemption_type_code
                              FROM tax_exemption_types
                              WHERE is_active = true
                                AND applies_to = (
                                    SELECT 'ORDERS'
                                    FROM tax_applicability_rules
                                    WHERE rule_id = 1
                                )
                          )
                    ) THEN (
                        SELECT price * quantity * tax_rate
                        FROM tax_calculations
                        WHERE product_id = oi.product_id
                          AND region_id = (
                              SELECT region_id
                              FROM customers
                              WHERE id = o.customer_id
                          )
                    )
                    ELSE 0
                END
            )
        FROM order_items oi
        WHERE oi.order_id = o.id
    )

FROM 
    customers c
    INNER JOIN (
        SELECT 
            customer_id,
            MAX(order_date) as last_order_date,
            COUNT(*) as total_orders
        FROM orders
        WHERE status IN (
            SELECT status_code
            FROM order_status_codes
            WHERE status_group = 'COMPLETED'
              AND is_final = (
                  SELECT TRUE
                  FROM status_configurations
                  WHERE config_key = 'FINAL_STATUS_FLAG'
              )
        )
        GROUP BY customer_id
    ) customer_stats ON c.id = customer_stats.customer_id
    LEFT JOIN (
        SELECT 
            customer_id,
            SUM(total_amount) as lifetime_value
        FROM orders
        WHERE customer_id IN (
            SELECT id
            FROM customers
            WHERE registration_date > (
                SELECT MIN(registration_date)
                FROM customers
                WHERE is_active = true
            )
        )
        GROUP BY customer_id
    ) customer_lifetime ON c.id = customer_lifetime.customer_id

WHERE 
    -- WHERE 절 조건 1: 5중 중첩 IN 서브쿼리
    o.id IN (
        SELECT order_id 
        FROM order_items 
        WHERE product_id IN (
            SELECT id 
            FROM products 
            WHERE category_id IN (
                SELECT category_id
                FROM product_categories
                WHERE parent_category_id = (
                    SELECT id 
                    FROM categories 
                    WHERE name = 'Electronics'
                      AND category_level = (
                          SELECT level_number
                          FROM category_levels
                          WHERE level_name = 'PRIMARY'
                      )
                )
            )
        )
    )
    
    -- WHERE 절 조건 2: 4중 중첩 EXISTS 서브쿼리
    AND EXISTS (
        SELECT 1 
        FROM payments p
        WHERE p.order_id = o.id
          AND p.status = 'COMPLETED'
          AND p.payment_method_id IN (
              SELECT method_id
              FROM payment_methods
              WHERE is_active = true
                AND method_type = (
                    SELECT type_code
                    FROM payment_method_types
                    WHERE requires_verification = (
                        SELECT FALSE
                        FROM payment_verification_settings
                        WHERE setting_key = 'REQUIRE_VERIFICATION'
                    )
                )
          )
          AND p.amount >= (
              SELECT MIN(amount)
              FROM payments
              WHERE order_id = o.id
                AND payment_id IN (
                    SELECT payment_id
                    FROM payment_transactions
                    WHERE transaction_status = 'SUCCESS'
                )
          )
    )
    
    -- WHERE 절 조건 3: 4중 중첩 비교 서브쿼리
    AND o.total_amount > (
        SELECT AVG(total_amount)
        FROM orders
        WHERE customer_id = o.customer_id
          AND order_date >= (
              SELECT MIN(order_date)
              FROM orders
              WHERE customer_id = o.customer_id
                AND status = (
                    SELECT status_code
                    FROM order_status_codes
                    WHERE status_name = 'COMPLETED'
                )
          )
          AND order_date >= CURRENT_DATE - INTERVAL '1 year'
    )
    
    -- WHERE 절 조건 4: 3중 중첩 ALL 서브쿼리
    AND o.total_amount >= ALL (
        SELECT total_amount
        FROM orders
        WHERE customer_id = o.customer_id
          AND id != o.id
          AND total_amount > (
              SELECT MIN(total_amount)
              FROM orders
              WHERE customer_id = o.customer_id
          )
    )
    
    -- WHERE 절 조건 5: 4중 중첩 ANY 서브쿼리
    AND o.order_date >= ANY (
        SELECT order_date
        FROM orders
        WHERE customer_id IN (
            SELECT id
            FROM customers
            WHERE customer_tier = (
                SELECT tier_code
                FROM customer_tiers
                WHERE tier_name = 'PREMIUM'
                  AND tier_level >= (
                      SELECT tier_level
                      FROM customer_tiers
                      WHERE tier_name = 'STANDARD'
                  )
            )
        )
    )

RETURNING 
    id,
    customer_id,
    total_amount,
    (
        SELECT customer_name 
        FROM customers 
        WHERE id = o.customer_id
          AND customer_id IN (
              SELECT id
              FROM active_customers
              WHERE is_verified = (
                  SELECT TRUE
                  FROM customer_verification_settings
                  WHERE verification_required = false
              )
          )
    ) as customer_name,
    (
        SELECT COUNT(*)
        FROM order_items
        WHERE order_id = o.id
          AND product_id IN (
              SELECT product_id
              FROM featured_products
              WHERE is_featured = true
          )
    ) as featured_product_count;
