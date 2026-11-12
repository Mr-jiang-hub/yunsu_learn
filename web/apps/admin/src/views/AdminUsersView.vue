<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MODULE_OPTIONS,
  adminUserApi,
  roleApi,
  type AdminStatus,
  type AdminUserItem,
  type ResetPasswordResult,
  type RoleDetail,
  type RoleItem,
} from '@/api/users'

const activeTab = ref('users')

/* ---------------------------------------------------------------- 后台账号 */

const loading = ref(false)
const items = ref<AdminUserItem[]>([])
const total = ref(0)
const roles = ref<RoleItem[]>([])
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  roleId: undefined as number | undefined,
  status: '' as AdminStatus | '',
})

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  username: '',
  displayName: '',
  password: '',
  roleId: undefined as number | undefined,
})

const resetVisible = ref(false)
const resetResult = ref<ResetPasswordResult | null>(null)

/* ------------------------------------------------------------------ 角色 */

const roleLoading = ref(false)
const roleDetail = ref<RoleDetail | null>(null)
const roleEditing = ref<number | null>(null)
const selectedModules = ref<string[]>([])
const roleSaving = ref(false)

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function fetchRoles(): Promise<void> {
  try {
    const result = await roleApi.list()
    roles.value = result.items
  } catch (error) {
    ElMessage.error(errorText(error, '加载角色失败'))
  }
}

async function fetchUsers(): Promise<void> {
  loading.value = true
  try {
    const result = await adminUserApi.list({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim(),
      roleId: query.roleId,
      status: query.status,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载后台账号失败'))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.page = 1
  void fetchUsers()
}

function openCreate(): void {
  editingId.value = null
  form.username = ''
  form.displayName = ''
  form.password = ''
  form.roleId = undefined
  dialogVisible.value = true
}

function openEdit(row: AdminUserItem): void {
  editingId.value = row.id
  form.username = row.username
  form.displayName = row.displayName
  form.password = ''
  form.roleId = row.roleId
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!form.displayName.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  if (form.roleId === undefined) {
    ElMessage.warning('请选择角色')
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      if (form.username.trim().length < 4) {
        ElMessage.warning('登录名至少 4 位')
        return
      }
      if (form.password.length < 8) {
        ElMessage.warning('初始密码至少 8 位')
        return
      }
      await adminUserApi.create({
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        password: form.password,
        roleId: form.roleId,
      })
      ElMessage.success('账号已创建，首次登录需修改密码')
    } else {
      // 账号不可改登录名/状态/密码，密码走重置接口
      await adminUserApi.update(editingId.value, {
        displayName: form.displayName.trim(),
        roleId: form.roleId,
      })
      ElMessage.success('账号已更新')
    }
    dialogVisible.value = false
    await fetchUsers()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: AdminUserItem): Promise<void> {
  const nextDisabled = row.status === 'ACTIVE'
  try {
    if (nextDisabled) {
      await ElMessageBox.confirm(
        `停用后「${row.displayName}」无法登录后台，登录态立即失效。确定停用？`,
        '停用账号',
        { type: 'warning' },
      )
      await adminUserApi.disable(row.id)
    } else {
      await adminUserApi.enable(row.id)
    }
    ElMessage.success(nextDisabled ? '已停用' : '已启用')
    await fetchUsers()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '操作失败'))
  }
}

async function resetPassword(row: AdminUserItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `将为「${row.displayName}」生成一次性临时密码，并注销其全部登录状态。继续？`,
      '重置密码',
      { type: 'warning' },
    )
    resetResult.value = await adminUserApi.resetPassword(row.id)
    resetVisible.value = true
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '重置密码失败'))
  }
}

async function copyPassword(): Promise<void> {
  if (!resetResult.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(resetResult.value.temporaryPassword)
    ElMessage.success('临时密码已复制')
  } catch {
    ElMessage.warning('浏览器未授权剪贴板，请手动选中复制')
  }
}

/* ------------------------------------------------------------------ 角色 */

async function openRole(role: RoleItem): Promise<void> {
  roleEditing.value = role.id
  roleLoading.value = true
  roleDetail.value = null
  selectedModules.value = []
  try {
    const data = await roleApi.detail(role.id)
    roleDetail.value = data
    selectedModules.value = [...data.modules]
  } catch (error) {
    ElMessage.error(errorText(error, '加载角色详情失败'))
  } finally {
    roleLoading.value = false
  }
}

async function saveRoleModules(): Promise<void> {
  if (roleEditing.value === null) {
    return
  }
  roleSaving.value = true
  try {
    const data = await roleApi.updateModules(roleEditing.value, selectedModules.value)
    roleDetail.value = data
    selectedModules.value = [...data.modules]
    ElMessage.success('权限已保存，60 秒内生效')
    await fetchRoles()
  } catch (error) {
    ElMessage.error(errorText(error, '保存权限失败'))
  } finally {
    roleSaving.value = false
  }
}

onMounted(() => {
  void fetchRoles()
  void fetchUsers()
})
</script>

<template>
  <div class="users-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane
        label="后台账号"
        name="users"
      >
        <div class="panel-toolbar">
          <el-input
            v-model="query.keyword"
            placeholder="登录名 / 姓名"
            clearable
            style="width: 200px"
            @clear="onSearch"
            @keyup.enter="onSearch"
          />
          <el-select
            v-model="query.roleId"
            placeholder="全部角色"
            clearable
            style="width: 150px"
            @change="onSearch"
          >
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
            @change="onSearch"
          >
            <el-option
              label="正常"
              value="ACTIVE"
            />
            <el-option
              label="已停用"
              value="DISABLED"
            />
          </el-select>
          <el-button
            type="primary"
            @click="onSearch"
          >
            查询
          </el-button>
          <div class="panel-toolbar-right">
            <el-button
              type="primary"
              @click="openCreate"
            >
              新增账号
            </el-button>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="items"
          stripe
          empty-text="暂无后台账号"
        >
          <el-table-column
            prop="id"
            label="ID"
            width="70"
          />
          <el-table-column
            prop="username"
            label="登录名"
            min-width="140"
          />
          <el-table-column
            prop="displayName"
            label="姓名"
            min-width="120"
          />
          <el-table-column
            prop="roleName"
            label="角色"
            min-width="130"
          />
          <el-table-column
            label="状态"
            width="90"
          >
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'ACTIVE' ? 'success' : 'info'"
                size="small"
              >
                {{ row.status === 'ACTIVE' ? '正常' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="最近登录"
            width="180"
          >
            <template #default="{ row }">
              {{ row.lastLoginAt || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="createdAt"
            label="创建时间"
            width="180"
          />
          <el-table-column
            label="操作"
            width="230"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="openEdit(row as AdminUserItem)"
              >
                编辑
              </el-button>
              <el-button
                link
                @click="resetPassword(row as AdminUserItem)"
              >
                重置密码
              </el-button>
              <el-button
                link
                :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
                @click="toggleStatus(row as AdminUserItem)"
              >
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="panel-pagination"
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="[10, 20, 50]"
          @current-change="(page: number) => { query.page = page; fetchUsers() }"
          @size-change="(size: number) => { query.size = size; query.page = 1; fetchUsers() }"
        />
      </el-tab-pane>

      <el-tab-pane
        label="角色权限"
        name="roles"
      >
        <el-row :gutter="16">
          <el-col :span="10">
            <el-table
              :data="roles"
              highlight-current-row
              empty-text="暂无角色"
              @row-click="(row: RoleItem) => openRole(row)"
            >
              <el-table-column
                prop="name"
                label="角色"
                min-width="120"
              />
              <el-table-column
                prop="code"
                label="编码"
                min-width="120"
              />
              <el-table-column
                prop="moduleCount"
                label="模块数"
                width="90"
              />
            </el-table>
            <p class="role-hint">
              固定三个预置角色，不支持新增或删除；点击行查看并调整模块权限。
            </p>
          </el-col>
          <el-col :span="14">
            <div
              v-loading="roleLoading"
              class="role-panel"
            >
              <template v-if="roleDetail">
                <h4 class="role-title">
                  {{ roleDetail.name }}（{{ roleDetail.code }}）
                </h4>
                <p class="role-desc">
                  {{ roleDetail.description || '—' }}
                </p>
                <el-alert
                  v-if="roleDetail.code === 'super_admin'"
                  type="info"
                  show-icon
                  :closable="false"
                  title="超级管理员拥有全部模块权限，不可修改"
                />
                <el-checkbox-group
                  v-else
                  v-model="selectedModules"
                  class="module-group"
                >
                  <el-checkbox
                    v-for="option in MODULE_OPTIONS"
                    :key="option.code"
                    :value="option.code"
                  >
                    {{ option.label }}
                  </el-checkbox>
                </el-checkbox-group>
                <div
                  v-if="roleDetail.code !== 'super_admin'"
                  class="role-actions"
                >
                  <el-button
                    type="primary"
                    :loading="roleSaving"
                    @click="saveRoleModules"
                  >
                    保存权限
                  </el-button>
                  <span class="role-hint">全量覆盖提交；权限缓存 60 秒内生效</span>
                </div>
              </template>
              <el-empty
                v-else
                description="请选择左侧角色"
              />
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增后台账号' : '编辑后台账号'"
      width="480px"
    >
      <el-form label-width="90px">
        <el-form-item label="登录名">
          <el-input
            v-model="form.username"
            :disabled="editingId !== null"
            maxlength="32"
            show-word-limit
            placeholder="4-32 位，创建后不可修改"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input
            v-model="form.displayName"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item
          v-if="editingId === null"
          label="初始密码"
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="32"
            placeholder="8-32 位，首次登录需修改"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="form.roleId"
            placeholder="选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resetVisible"
      title="临时密码已生成"
      width="440px"
    >
      <p class="reset-line">
        该密码只显示这一次，请立即转交给对应管理员：
      </p>
      <div class="reset-password">
        {{ resetResult?.temporaryPassword }}
      </div>
      <p class="reset-line">
        对方下次登录必须修改密码；已有登录状态已全部失效。
      </p>
      <template #footer>
        <el-button @click="resetVisible = false">
          关闭
        </el-button>
        <el-button
          type="primary"
          @click="copyPassword"
        >
          复制密码
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.users-page {
  padding: 4px 8px 16px;
}

.panel-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.panel-toolbar-right {
  margin-left: auto;
}

.panel-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.role-panel {
  min-height: 240px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.role-title {
  margin: 0 0 8px;
  font-size: 15px;
}

.role-desc {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.module-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
}

.role-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 20px;
}

.role-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.reset-line {
  margin: 0 0 10px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.reset-password {
  margin-bottom: 12px;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-size: 18px;
  letter-spacing: 1px;
  text-align: center;
  user-select: all;
}
</style>
