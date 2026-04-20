<template>
  <header class="app-header">
    <div class="header-content">
      <h1>DEX Aggregator</h1>
      <nav>
        <router-link to="/">Overview</router-link>
        <router-link to="/route">Route</router-link>
        <router-link to="/blockchain">Blockchain</router-link>
        <router-link to="/monitor">Monitor</router-link>
      </nav>
    </div>
  </header>
</template>

<script setup>
</script>

<style scoped>
.app-header {
  background-color: #fff;
  border-bottom: 1px solid #e0e0e0;
  padding: 20px 0;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

h1 {
  font-size: 24px;
  color: #333;
}

nav {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

nav a {
  text-decoration: none;
  color: #666;
  font-weight: 500;
  transition: color 0.3s;
}

nav a:hover,
nav a.router-link-active {
  color: #409eff;
}

@media (max-width: 760px) {
  .app-header {
    padding: 16px 0;
  }

  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }

  nav {
    gap: 16px;
  }
}
</style>
