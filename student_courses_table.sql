-- 创建学生选课表（根据实际表结构）
CREATE TABLE `student_courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '学号',
  `course_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '课程编号',
  `semester` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '学期',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_select` (`student_id`,`course_id`,`semester`) USING BTREE,
  KEY `fk_sc_course` (`course_id`) USING BTREE,
  CONSTRAINT `fk_sc_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_sc_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='学生选课表';

-- 插入测试数据
INSERT INTO `student_courses` (`student_id`, `course_id`, `semester`) VALUES
('2021001234', 'CS202', '2024-2025-1'),
('2021005678', 'CS202', '2024-2025-1'),
('2021001234', 'CS301', '2024-2025-1'),
('2021005678', 'CS301', '2024-2025-1');