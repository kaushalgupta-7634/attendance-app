-- Database DDL Schema Initialization

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    class_name VARCHAR(255),
    current_session_id VARCHAR(255),
    reset_token VARCHAR(255),
    reset_token_expiry TIMESTAMP
);

CREATE TABLE IF NOT EXISTS class_courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    class_name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    class_code VARCHAR(100) NOT NULL UNIQUE,
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS enrollments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    class_course_id BIGINT NOT NULL,
    enrolled_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollment_course FOREIGN KEY (class_course_id) REFERENCES class_courses(id) ON DELETE CASCADE,
    CONSTRAINT uk_student_class_course UNIQUE (student_id, class_course_id)
);

CREATE TABLE IF NOT EXISTS class_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    class_course_id BIGINT,
    class_name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL DEFAULT 'UNSPECIFIED',
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    classroom_lat DOUBLE NOT NULL,
    classroom_lng DOUBLE NOT NULL,
    radius_meters DOUBLE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    passcode VARCHAR(10),
    expected_wifi_ssid VARCHAR(255),
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_session_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_course FOREIGN KEY (class_course_id) REFERENCES class_courses(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    marked_at TIMESTAMP NOT NULL,
    student_lat DOUBLE NOT NULL,
    student_lng DOUBLE NOT NULL,
    status VARCHAR(50) NOT NULL,
    manually_overridden BOOLEAN DEFAULT FALSE,
    override_reason VARCHAR(500),
    overridden_by_id BIGINT,
    student_wifi_ssid VARCHAR(255),
    wifi_mismatch_warning BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES class_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_record_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_record_overridden_by FOREIGN KEY (overridden_by_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_session_student UNIQUE (session_id, student_id)
);

CREATE TABLE IF NOT EXISTS assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    class_name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    pdf_file_path VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    due_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_assignment_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE
);
