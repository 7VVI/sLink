-- ---------------------------------------------------------------------------
-- 短链系统数据库初始化脚本（docker-entrypoint-initdb.d 自动执行）
-- 拓扑：主库 short_link_main（单表）+ 分片库 short_link_ds0~3（short_url_0~15）
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS short_link_main DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS short_link_ds0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS short_link_ds1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS short_link_ds2 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS short_link_ds3 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 主库：用户 / 发号器 / 统计归档
-- ---------------------------------------------------------------------------
USE short_link_main;

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    username    VARCHAR(32)  NOT NULL COMMENT '用户名',
    password    VARCHAR(64)  NOT NULL COMMENT 'BCrypt密文',
    nickname    VARCHAR(32)  NULL COMMENT '昵称',
    role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN/USER',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-正常 0-禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT '系统用户';

CREATE TABLE IF NOT EXISTS leaf_alloc (
    biz_tag     VARCHAR(128) NOT NULL COMMENT '发号业务标签',
    max_id      BIGINT       NOT NULL DEFAULT 1000000000 COMMENT '号段右边界（起始10亿防枚举）',
    step        INT          NOT NULL DEFAULT 100000 COMMENT '号段步长',
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_tag)
) ENGINE = InnoDB COMMENT '号段发号器';

INSERT INTO leaf_alloc (biz_tag, max_id, step)
VALUES ('short_url', 1000000000, 100000)
ON DUPLICATE KEY UPDATE update_time = update_time;

CREATE TABLE IF NOT EXISTS short_url_stats (
    short_code VARCHAR(8) NOT NULL COMMENT '短码',
    stat_date  DATE       NOT NULL COMMENT '统计日期',
    pv         BIGINT     NOT NULL DEFAULT 0 COMMENT '当日PV',
    uv         BIGINT     NOT NULL DEFAULT 0 COMMENT '当日UV',
    PRIMARY KEY (short_code, stat_date)
) ENGINE = InnoDB COMMENT '短链按日统计归档';

-- ---------------------------------------------------------------------------
-- 分片库：short_url_{0..15} × 4 库 = 64 分片
-- ---------------------------------------------------------------------------
DELIMITER $$

CREATE PROCEDURE create_short_url_shards()
BEGIN
    DECLARE ds INT DEFAULT 0;
    DECLARE tb INT DEFAULT 0;
    WHILE ds < 4 DO
        WHILE tb < 16 DO
            SET @ddl = CONCAT(
                'CREATE TABLE IF NOT EXISTS short_link_ds', ds, '.short_url_', tb, ' (',
                '  id          BIGINT        NOT NULL COMMENT ''发号器全局ID'',',
                '  short_code  VARCHAR(8)    NOT NULL COMMENT ''短码（分片键）'',',
                '  long_url    VARCHAR(2048) NOT NULL COMMENT ''长链接'',',
                '  title       VARCHAR(128)  NULL COMMENT ''标题'',',
                '  user_id     BIGINT        NOT NULL COMMENT ''创建人'',',
                '  expire_time DATETIME      NULL COMMENT ''过期时间'',',
                '  status      TINYINT       NOT NULL DEFAULT 1 COMMENT ''1-正常 0-下线 2-删除'',',
                '  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,',
                '  update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,',
                '  PRIMARY KEY (id),',
                '  UNIQUE KEY uk_code (short_code),',
                '  KEY idx_user_id_create (user_id, create_time)',
                ') ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ''短链分片表'''
            );
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SET tb = tb + 1;
        END WHILE;
        SET tb = 0;
        SET ds = ds + 1;
    END WHILE;
END$$

CALL create_short_url_shards()$$

DROP PROCEDURE create_short_url_shards$$

DELIMITER ;
