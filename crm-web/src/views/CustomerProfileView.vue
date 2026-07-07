<template>
  <section>
    <div class="toolbar">
      <div class="page-title">客户档案</div>
      <el-button text @click="$router.back()">返回</el-button>
    </div>

    <div v-loading="loading" class="profile-page">
      <section class="panel profile-summary">
        <div>
          <span>主档客资</span>
          <strong>{{ profile.rootCustomerCode || '-' }}</strong>
        </div>
        <div>
          <span>主联系方式</span>
          <strong>{{ profile.primaryContactInfo || '待补充' }}</strong>
        </div>
        <div>
          <span>历史需求</span>
          <strong>{{ profile.totalDemands || 0 }} 次</strong>
        </div>
      </section>

      <section class="panel">
        <div class="section-head">
          <h2>需求明细</h2>
          <p>同一客户多次出行需求会聚合在这里，方便查看完整沟通背景。</p>
        </div>
        <div v-if="profile.demands?.length" class="demand-list">
          <article v-for="item in profile.demands" :key="item.customerCode" class="demand-card">
            <button type="button" class="demand-main" @click="goClue(item.customerCode)">
              <div>
                <strong>第 {{ item.demandSequence || 1 }} 次需求 · {{ item.customerCode }}</strong>
                <span>{{ sourcePlatformText(item.sourcePlatform) }} · {{ addMethodText(item.addMethod) }}</span>
              </div>
              <StatusTag :status="item.status" />
            </button>
            <dl>
              <div><dt>运营</dt><dd>{{ item.uploader || '-' }}（{{ item.uploaderEmployeeCode || '-' }}）</dd></div>
              <div><dt>销售</dt><dd>{{ item.assignedSales || '未分配' }}（{{ item.assignedSalesEmployeeCode || '-' }}）</dd></div>
              <div><dt>创建时间</dt><dd>{{ item.createdAt || '-' }}</dd></div>
            </dl>
            <div v-if="item.followRecords?.length" class="follow-list">
              <div v-for="record in item.followRecords" :key="`${item.customerCode}-${record.createdAt}-${record.remark}`">
                <strong>{{ record.operator }}（{{ record.operatorCode }}）</strong>
                <span>{{ record.createdAt }}</span>
                <p>{{ record.remark }}</p>
              </div>
            </div>
            <el-empty v-else description="该次需求暂无跟进记录" />
          </article>
        </div>
        <el-empty v-else description="暂无客户需求数据" />
      </section>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCustomerProfile } from '../api/clue'
import StatusTag from '../components/StatusTag.vue'
import { showError } from '../utils/feedback'
import { addMethodText, sourcePlatformText } from '../utils/status'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const profile = reactive({
  rootCustomerCode: '',
  primaryContactInfo: '',
  totalDemands: 0,
  demands: []
})

onMounted(loadProfile)

async function loadProfile() {
  loading.value = true
  try {
    const res = await getCustomerProfile(route.params.rootCustomerCode)
    Object.assign(profile, res.data || {})
  } catch (error) {
    await showError(error.message || '客户档案加载失败')
    router.back()
  } finally {
    loading.value = false
  }
}

function goClue(customerCode) {
  if (!customerCode) return
  router.push(`/clues/${customerCode}`)
}
</script>

<style scoped>
.profile-page {
  display: grid;
  gap: 14px;
}

.profile-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.profile-summary div {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.7);
}

.profile-summary span,
.section-head p,
.demand-main span,
.demand-card dt,
.follow-list span,
.follow-list p {
  color: var(--text-muted);
}

.profile-summary strong {
  display: block;
  margin-top: 8px;
  color: var(--text-strong);
  font-size: 18px;
  overflow-wrap: anywhere;
}

.section-head h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 18px;
}

.section-head p {
  margin: 6px 0 0;
}

.demand-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.demand-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(178, 174, 250, 0.24);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.62);
}

.demand-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-main);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.demand-main strong,
.demand-main span {
  display: block;
}

.demand-main strong {
  color: var(--text-strong);
}

.demand-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.demand-card dt {
  font-size: 12px;
}

.demand-card dd {
  margin: 4px 0 0;
  color: var(--text-main);
  overflow-wrap: anywhere;
}

.follow-list {
  display: grid;
  gap: 8px;
}

.follow-list div {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(79, 200, 120, 0.11), rgba(178, 174, 250, 0.13));
}

.follow-list strong {
  color: var(--text-strong);
}

.follow-list p {
  margin: 0;
}

@media (max-width: 760px) {
  .profile-summary,
  .demand-card dl {
    grid-template-columns: 1fr;
  }

  .demand-main {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
