<template>
  <div class="management">
    <h2>系统管理</h2>
    <el-card>
      <p>欢迎使用管理员功能</p>
      <el-button type="primary">用户管理</el-button>
      <el-button type="primary">课程管理</el-button>
      <el-button type="primary">数据统计</el-button>
    </el-card>
  </div>
</template>

<script setup>

</script>

<style scoped>
.management {
  h2 {
    margin-bottom: 20px;
    color: #303133;
  }
  .el-button {
    margin-right: 10px;
    margin-top: 10px;
  }
}
</style>