<script setup lang="ts">
import { ShieldX } from '@lucide/vue'
import { Button } from '@/components/ui/button'
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-3 py-24 text-center">
    <ShieldX class="text-muted-foreground size-10" />
    <h1 class="text-lg font-semibold">Bu boʻlim uchun ruxsatingiz yoʻq</h1>
    <p class="text-muted-foreground text-sm">Kerak boʻlsa administratorga murojaat qiling.</p>
    <Button variant="outline" as-child><RouterLink to="/profile">Profilga</RouterLink></Button>
  </div>
</template>
