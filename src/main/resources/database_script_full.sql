-- ====================================================================================
-- SMART PLANNER - PRO DATABASE SCRIPT (FULL & FIXED)
-- Platform: SQL Server
-- Description: Creates all tables, constraints, indexes, and initial seed data.
-- ====================================================================================

-- 1. Create Database (if not exists)
IF DB_ID('SmartPlanner') IS NULL
BEGIN
    CREATE DATABASE SmartPlanner;
END
GO

USE SmartPlanner;
GO

-- 2. Clean up existing tables (Order matters due to FKs)
-- Dropping in reverse dependency order

IF OBJECT_ID('dbo.session_reminders', 'U') IS NOT NULL DROP TABLE dbo.session_reminders;
IF OBJECT_ID('dbo.study_sessions', 'U') IS NOT NULL DROP TABLE dbo.study_sessions;
IF OBJECT_ID('dbo.schedule_evaluations', 'U') IS NOT NULL DROP TABLE dbo.schedule_evaluations;
IF OBJECT_ID('dbo.study_schedules', 'U') IS NOT NULL DROP TABLE dbo.study_schedules;
IF OBJECT_ID('dbo.tasks', 'U') IS NOT NULL DROP TABLE dbo.tasks;
IF OBJECT_ID('dbo.courses', 'U') IS NOT NULL DROP TABLE dbo.courses;
IF OBJECT_ID('dbo.availability_slots', 'U') IS NOT NULL DROP TABLE dbo.availability_slots;
IF OBJECT_ID('dbo.study_preferences', 'U') IS NOT NULL DROP TABLE dbo.study_preferences;

IF OBJECT_ID('dbo.users', 'U') IS NOT NULL DROP TABLE dbo.users;
GO

-- 3. Create Tables

-- USERS
CREATE TABLE users (
    id INT IDENTITY(1,1) PRIMARY KEY,
    email NVARCHAR(200) NOT NULL UNIQUE,
    password_hash NVARCHAR(200) NOT NULL,
    full_name NVARCHAR(200) NOT NULL,
    status NVARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE
    created_at DATETIME2 DEFAULT SYSUTCDATETIME()
);
GO

-- STUDY PREFERENCES
CREATE TABLE study_preferences (
    id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    plan_start_date DATE NULL,
    plan_end_date DATE NULL,
    max_hours_per_day DECIMAL(4,2) NOT NULL DEFAULT 2.00,
    allowed_days NVARCHAR(100) DEFAULT 'MON,TUE,WED,THU,FRI',
    block_minutes INT DEFAULT 60,
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NULL,
    CONSTRAINT fk_pref_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
GO

-- AVAILABILITY SLOTS
CREATE TABLE availability_slots (
    id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    day_of_week INT NOT NULL, -- 1=Mon, 7=Sun
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active BIT DEFAULT 1,
    deleted BIT DEFAULT 0,
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_avail_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_avail_user ON availability_slots(user_id);
GO

-- COURSES
CREATE TABLE courses (
    id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    name NVARCHAR(200) NOT NULL,
    priority NVARCHAR(20) DEFAULT 'MEDIUM', -- HIGH, MEDIUM, LOW
    deadline_date DATE NULL,
    total_hours DECIMAL(5,2) DEFAULT 0,
    status NVARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, ARCHIVED
    deleted BIT DEFAULT 0,
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NULL,
    CONSTRAINT fk_courses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_courses_user ON courses(user_id);
-- Filtered Unique Index: prevents duplicate names per user for active courses only
CREATE UNIQUE INDEX uq_course_name_user ON courses(user_id, name) WHERE deleted = 0;
GO

-- TASKS
CREATE TABLE tasks (
    id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    course_id INT NULL,
    title NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NULL,
    type NVARCHAR(50) DEFAULT 'OTHER', -- ASSIGNMENT, QUIZ, EXAM...
    priority NVARCHAR(20) DEFAULT 'MEDIUM',
    deadline_date DATE NOT NULL,
    estimated_hours DECIMAL(5,2) DEFAULT 1.0,
    remaining_hours DECIMAL(5,2) DEFAULT 1.0,
    status NVARCHAR(20) DEFAULT 'OPEN', -- OPEN, DONE, CANCELED
    deleted BIT DEFAULT 0,
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NULL,
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE NO ACTION, -- Avoiding multiple cascade paths
    CONSTRAINT fk_tasks_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);
CREATE INDEX idx_tasks_user_deadline ON tasks(user_id, deadline_date);
GO

-- STUDY SCHEDULES (Weekly container)
CREATE TABLE study_schedules (
    id INT IDENTITY(1,1) PRIMARY KEY,
    user_id INT NOT NULL,
    week_start_date DATE NOT NULL,
    generated_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    confidence_score INT DEFAULT 0,
    warnings NVARCHAR(1000) NULL,
    CONSTRAINT fk_schedule_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_schedule_user_week UNIQUE(user_id, week_start_date)
);
GO

-- SCHEDULE EVALUATIONS
CREATE TABLE schedule_evaluations (
    id INT IDENTITY(1,1) PRIMARY KEY,
    schedule_id INT NOT NULL UNIQUE,
    score INT NOT NULL,
    level NVARCHAR(20), -- HIGH, MEDIUM, LOW
    feedback NVARCHAR(MAX),
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_eval_schedule FOREIGN KEY (schedule_id) REFERENCES study_schedules(id) ON DELETE CASCADE
);
GO

-- STUDY SESSIONS
CREATE TABLE study_sessions (
    id INT IDENTITY(1,1) PRIMARY KEY,
    schedule_id INT NOT NULL,
    course_id INT NOT NULL,
    task_id INT NULL,
    session_date DATE NOT NULL,
    slot_start TIME NOT NULL,
    slot_end TIME NOT NULL,
    planned_duration INT NOT NULL,
    status NVARCHAR(20) DEFAULT 'SCHEDULED', -- SCHEDULED, IN_PROGRESS, COMPLETED, EXPIRED
    actual_duration INT NULL,
    difficulty NVARCHAR(20) NULL,
    note NVARCHAR(MAX) NULL,
    ai_status NVARCHAR(20) NULL,
    ai_quality_score INT NULL,
    ai_summary NVARCHAR(MAX) NULL,
    ai_next_action NVARCHAR(MAX) NULL,
    ai_revision_suggestion NVARCHAR(MAX) NULL,
    ai_analyzed_at DATETIME2 NULL,
    ai_error NVARCHAR(MAX) NULL,
    started_at DATETIME2 NULL,
    completed_at DATETIME2 NULL,
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NULL,
    CONSTRAINT fk_session_schedule FOREIGN KEY (schedule_id) REFERENCES study_schedules(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE NO ACTION,
    CONSTRAINT fk_session_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE NO ACTION
);
CREATE INDEX idx_sessions_schedule ON study_sessions(schedule_id);
CREATE INDEX idx_sessions_date ON study_sessions(session_date);
GO

-- SESSION REMINDERS
CREATE TABLE session_reminders (
    id INT IDENTITY(1,1) PRIMARY KEY,
    session_id INT NOT NULL,
    reminder_time DATETIME2 NOT NULL,
    channel NVARCHAR(20) DEFAULT 'EMAIL',
    status NVARCHAR(20) DEFAULT 'PENDING',
    created_at DATETIME2 DEFAULT SYSUTCDATETIME(),
    sent_at DATETIME2 NULL,
    CONSTRAINT fk_reminder_session FOREIGN KEY (session_id) REFERENCES study_sessions(id) ON DELETE CASCADE
);
GO



-- 4. Initial Seed Data (Optional - Demo User)
-- Password is '123456' (BCrypt encoded)
INSERT INTO users (email, password_hash, full_name, status)
VALUES ('demo1@smartplanner.com', '$2a$10$YHO7MNHvqBRg6vlYbvV0Ouj5jhIFj5r7ONhGC/Ym1tRxxoXA1MadO', 'Demo User', 'ACTIVE');
GO

-- Demo Data for demo1@smartplanner.com
DECLARE @UserId INT = (SELECT id FROM users WHERE email = 'demo1@smartplanner.com');

IF @UserId IS NOT NULL
BEGIN
    -- Preferences
    INSERT INTO study_preferences (user_id, plan_start_date, plan_end_date, max_hours_per_day, allowed_days, block_minutes)
    VALUES (@UserId, CAST(GETDATE() AS DATE), CAST(DATEADD(month, 3, GETDATE()) AS DATE), 4.00, 'MON,TUE,WED,THU,FRI,SAT', 60);

    -- Courses
    INSERT INTO courses (user_id, name, priority, total_hours, status)
    VALUES (@UserId, N'Phát triển ứng dụng Web', 'HIGH', 45.0, 'ACTIVE'),
           (@UserId, N'Cơ sở dữ liệu', 'MEDIUM', 30.0, 'ACTIVE'),
           (@UserId, N'Trí tuệ nhân tạo', 'HIGH', 50.0, 'ACTIVE');

    -- Tasks
    DECLARE @C1 INT = (SELECT id FROM courses WHERE user_id = @UserId AND name = N'Phát triển ứng dụng Web');
    DECLARE @C2 INT = (SELECT id FROM courses WHERE user_id = @UserId AND name = N'Cơ sở dữ liệu');
    
    INSERT INTO tasks (user_id, course_id, title, description, type, priority, deadline_date, estimated_hours, remaining_hours, status)
    VALUES (@UserId, @C1, N'Hoàn thiện Frontend Login', N'Sử dụng Tailwind CSS và Vanilla JS', 'ASSIGNMENT', 'HIGH', CAST(DATEADD(day, 5, GETDATE()) AS DATE), 4.0, 4.0, 'OPEN'),
           (@UserId, @C1, N'Xây dựng REST API', N'Spring Boot & SQL Server', 'PROJECT', 'HIGH', CAST(DATEADD(day, 10, GETDATE()) AS DATE), 12.0, 12.0, 'OPEN'),
           (@UserId, @C2, N'Thiết kế ERD cho dự án', N'Vẽ sơ đồ quan hệ thực thể', 'QUIZ', 'MEDIUM', CAST(DATEADD(day, 3, GETDATE()) AS DATE), 2.0, 2.0, 'OPEN');
END
GO
