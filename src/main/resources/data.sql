-- Seed Test Data (1 Teacher + 5 Test Students + 1 Class Course + Enrollments + 1 Active Class Session)

-- 1. Seed Teacher (username: teacher1, password: password123)
MERGE INTO users (id, name, username, email, password, role, pin_attempt_count, pin_request_count, enabled) KEY(id) VALUES 
(1, 'Prof. Alan Turing', 'teacher1', 'teacher1@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xD0YM1b62Is19j5u', 'TEACHER', 0, 0, true);

-- 2. Seed 5 Test Students (username: student1..student5, password: password123)
MERGE INTO users (id, name, username, email, password, role, pin_attempt_count, pin_request_count, enabled) KEY(id) VALUES 
(2, 'Alice Smith', 'student1', 'alice@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xD0YM1b62Is19j5u', 'STUDENT', 0, 0, true),
(3, 'Bob Johnson', 'student2', 'bob@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xD0YM1b62Is19j5u', 'STUDENT', 0, 0, true),
(4, 'Charlie Brown', 'student3', 'charlie@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xD0YM1b62Is19j5u', 'STUDENT', 0, 0, true),
(5, 'Diana Prince', 'student4', 'diana@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xD0YM1b62Is19j5u', 'STUDENT', 0, 0, true),
(6, 'Evan Wright', 'student5', 'evan@example.com', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xD0YM1b62Is19j5u', 'STUDENT', 0, 0, true);

-- 3. Seed Class Course (classCode: CS101-A)
MERGE INTO class_courses (id, teacher_id, class_name, subject, class_code) KEY(id) VALUES
(1, 1, 'CS101 - Algorithms & Data Structures', 'Computer Science', 'CS101-A');

-- 4. Seed Enrollments for student1 (Alice) & student2 (Bob) into CS101-A
MERGE INTO enrollments (id, student_id, class_course_id, enrolled_at) KEY(id) VALUES
(1, 2, 1, '2026-08-01 00:00:00'),
(2, 3, 1, '2026-08-01 00:00:00');

-- 5. Seed 1 Active Class Session
MERGE INTO class_sessions (id, teacher_id, class_course_id, class_name, start_time, end_time, classroom_lat, classroom_lng, radius_meters, active) KEY(id) VALUES 
(1, 1, 1, 'CS101 - Algorithms & Data Structures', '2026-08-01 00:00:00', '2026-12-31 23:59:59', 12.9716, 77.5946, 0.0, true);
