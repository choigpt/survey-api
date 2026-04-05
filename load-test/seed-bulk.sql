-- 부하 테스트용 대량 데이터 삽입
-- 설문 1,000개 x 응답 2,000개 = 약 200만건
-- 실행: mysql -u root survey_db < load-test/seed-bulk.sql

SET autocommit = 0;
SET unique_checks = 0;
SET foreign_key_checks = 0;

-- 1. 설문 1,000개
DROP PROCEDURE IF EXISTS insert_surveys;
DELIMITER //
CREATE PROCEDURE insert_surveys()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 1000 DO
        INSERT INTO surveys (title, description, start_date, end_date, status, created_at)
        VALUES (
            CONCAT('부하테스트 설문 #', i),
            CONCAT('부하 테스트를 위한 자동 생성 설문입니다. (', i, '번)'),
            '2026-01-01',
            '2026-12-31',
            'ACTIVE',
            NOW()
        );
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;
CALL insert_surveys();
DROP PROCEDURE insert_surveys;
COMMIT;

-- 2. 질문 4개/설문 = 4,000개
DROP PROCEDURE IF EXISTS insert_questions;
DELIMITER //
CREATE PROCEDURE insert_questions()
BEGIN
    DECLARE s_id INT DEFAULT 1;
    DECLARE q_start INT;
    WHILE s_id <= 1000 DO
        -- SINGLE_CHOICE
        INSERT INTO questions (content, type, order_index, required, survey_id)
        VALUES (CONCAT('[', s_id, '] 단일 선택 질문'), 'SINGLE_CHOICE', 1, 1, s_id);

        -- MULTI_CHOICE
        INSERT INTO questions (content, type, order_index, required, survey_id)
        VALUES (CONCAT('[', s_id, '] 복수 선택 질문'), 'MULTI_CHOICE', 2, 1, s_id);

        -- RATING
        INSERT INTO questions (content, type, order_index, required, survey_id)
        VALUES (CONCAT('[', s_id, '] 별점 평가'), 'RATING', 3, 1, s_id);

        -- TEXT
        INSERT INTO questions (content, type, order_index, required, survey_id)
        VALUES (CONCAT('[', s_id, '] 자유 서술'), 'TEXT', 4, 0, s_id);

        SET s_id = s_id + 1;
    END WHILE;
END //
DELIMITER ;
CALL insert_questions();
DROP PROCEDURE insert_questions;
COMMIT;

-- 3. 선택지: SINGLE_CHOICE 5개 + MULTI_CHOICE 5개 = 10개/설문 = 10,000개
DROP PROCEDURE IF EXISTS insert_options;
DELIMITER //
CREATE PROCEDURE insert_options()
BEGIN
    DECLARE q_id INT;
    DECLARE i INT;

    -- question id는 순서대로 생성됨: 설문1의 질문은 1,2,3,4 / 설문2는 5,6,7,8 ...
    -- SINGLE_CHOICE = (s_id - 1) * 4 + 1
    -- MULTI_CHOICE  = (s_id - 1) * 4 + 2

    DECLARE s_id INT DEFAULT 1;
    WHILE s_id <= 1000 DO
        -- SINGLE_CHOICE 선택지
        SET q_id = (s_id - 1) * 4 + 1;
        SET i = 1;
        WHILE i <= 5 DO
            INSERT INTO question_options (content, order_index, question_id)
            VALUES (CONCAT('선택지 ', i), i, q_id);
            SET i = i + 1;
        END WHILE;

        -- MULTI_CHOICE 선택지
        SET q_id = (s_id - 1) * 4 + 2;
        SET i = 1;
        WHILE i <= 5 DO
            INSERT INTO question_options (content, order_index, question_id)
            VALUES (CONCAT('항목 ', i), i, q_id);
            SET i = i + 1;
        END WHILE;

        SET s_id = s_id + 1;
    END WHILE;
END //
DELIMITER ;
CALL insert_options();
DROP PROCEDURE insert_options;
COMMIT;

-- 4. 응답 2,000개/설문 = 2,000,000건 (survey_responses)
-- 배치 단위로 삽입
DROP PROCEDURE IF EXISTS insert_responses;
DELIMITER //
CREATE PROCEDURE insert_responses()
BEGIN
    DECLARE s_id INT DEFAULT 1;
    DECLARE r INT;
    WHILE s_id <= 1000 DO
        SET r = 1;
        WHILE r <= 2000 DO
            INSERT INTO survey_responses (respondent, survey_id, submitted_at)
            VALUES (CONCAT('user_', s_id, '_', r), s_id, NOW());
            SET r = r + 1;
        END WHILE;

        IF s_id MOD 50 = 0 THEN
            COMMIT;
            SELECT CONCAT('응답 진행: ', s_id, '/1000 설문') AS progress;
        END IF;

        SET s_id = s_id + 1;
    END WHILE;
END //
DELIMITER ;
CALL insert_responses();
DROP PROCEDURE insert_responses;
COMMIT;

-- 5. 답변 (answers) - 응답당 4개 질문 = 약 800만건
-- 가장 큰 테이블이므로 벌크 INSERT ... SELECT 사용
DROP PROCEDURE IF EXISTS insert_answers;
DELIMITER //
CREATE PROCEDURE insert_answers()
BEGIN
    DECLARE s_id INT DEFAULT 1;
    DECLARE q_single INT;
    DECLARE q_multi INT;
    DECLARE q_rating INT;
    DECLARE q_text INT;
    DECLARE opt_base_single INT;
    DECLARE opt_base_multi INT;

    WHILE s_id <= 1000 DO
        SET q_single = (s_id - 1) * 4 + 1;
        SET q_multi  = (s_id - 1) * 4 + 2;
        SET q_rating = (s_id - 1) * 4 + 3;
        SET q_text   = (s_id - 1) * 4 + 4;

        -- 선택지 ID: SINGLE은 (s_id-1)*10 + 1~5, MULTI는 (s_id-1)*10 + 6~10
        SET opt_base_single = (s_id - 1) * 10 + 1;
        SET opt_base_multi  = (s_id - 1) * 10 + 6;

        -- SINGLE_CHOICE 답변
        INSERT INTO answers (question_id, selected_option_id, text_value, survey_response_id)
        SELECT q_single,
               opt_base_single + (sr.id MOD 5),
               NULL,
               sr.id
        FROM survey_responses sr
        WHERE sr.survey_id = s_id;

        -- MULTI_CHOICE 답변
        INSERT INTO answers (question_id, selected_option_id, text_value, survey_response_id)
        SELECT q_multi,
               opt_base_multi + (sr.id MOD 5),
               NULL,
               sr.id
        FROM survey_responses sr
        WHERE sr.survey_id = s_id;

        -- RATING 답변
        INSERT INTO answers (question_id, selected_option_id, text_value, survey_response_id)
        SELECT q_rating,
               NULL,
               CAST((sr.id MOD 5) + 1 AS CHAR),
               sr.id
        FROM survey_responses sr
        WHERE sr.survey_id = s_id;

        -- TEXT 답변
        INSERT INTO answers (question_id, selected_option_id, text_value, survey_response_id)
        SELECT q_text,
               NULL,
               ELT((sr.id MOD 5) + 1, '매우 좋음', '좋음', '보통', '개선 필요', '불만족'),
               sr.id
        FROM survey_responses sr
        WHERE sr.survey_id = s_id;

        IF s_id MOD 50 = 0 THEN
            COMMIT;
            SELECT CONCAT('답변 진행: ', s_id, '/1000 설문') AS progress;
        END IF;

        SET s_id = s_id + 1;
    END WHILE;
END //
DELIMITER ;
CALL insert_answers();
DROP PROCEDURE insert_answers;
COMMIT;

SET autocommit = 1;
SET unique_checks = 1;
SET foreign_key_checks = 1;

-- 결과 확인
SELECT '===== 시드 데이터 완료 =====' AS status;
SELECT 'surveys' AS table_name, COUNT(*) AS count FROM surveys
UNION ALL
SELECT 'questions', COUNT(*) FROM questions
UNION ALL
SELECT 'question_options', COUNT(*) FROM question_options
UNION ALL
SELECT 'survey_responses', COUNT(*) FROM survey_responses
UNION ALL
SELECT 'answers', COUNT(*) FROM answers;
