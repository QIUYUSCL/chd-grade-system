// 修改前
claims.put("userId", userData.get("student_id") != null ?
        userData.get("student_id") : userData.get("teacher_id"));

// 修改后
claims.put("userId", userData.get("student_id") != null ?
        userData.get("student_id") : (userData.get("teacher_id") != null ?
        userData.get("teacher_id") : userData.get("admin_id")));