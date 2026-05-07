<template>
  <div v-if="dialog" class="dialog-overlay" @click.self="closeDialog">
    <div class="dialog-content">
      <div class="dialog-header">
        <h3 class="dialog-title">Добавить новое устройство</h3>
        <button class="close-button" @click="closeDialog">×</button>
      </div>
      
      <div class="dialog-body">
        <form @submit.prevent="submit" class="device-form">
          <div class="form-group">
            <label for="name">Название устройства *</label>
            <input
              id="name"
              v-model="formData.name"
              type="text"
              required
              placeholder="Введите название"
              class="form-input"
            />
            <div v-if="errors.name" class="error-text">{{ errors.name }}</div>
          </div>

          <div class="form-group">
            <label for="registrationNumber">Регистрационный номер *</label>
            <input
              id="registrationNumber"
              v-model="formData.registrationNumber"
              type="text"
              required
              placeholder="Введите регистрационный номер"
              class="form-input"
            />
            <div v-if="errors.registrationNumber" class="error-text">{{ errors.registrationNumber }}</div>
          </div>

          <div class="form-group">
            <label for="deviceId">ID устройства (уникальный) *</label>
            <input
              id="deviceId"
              v-model="formData.deviceId"
              type="text"
              required
              placeholder="Введите уникальный ID устройства"
              class="form-input"
            />
            <div v-if="errors.deviceId" class="error-text">{{ errors.deviceId }}</div>
          </div>

          <div class="form-group">
            <label for="typeId">Тип устройства (ID) *</label>
            <input
              id="typeId"
              v-model.number="formData.typeId"
              type="number"
              required
              min="0"
              placeholder="Введите числовой ID типа"
              class="form-input"
            />
            <div v-if="errors.typeId" class="error-text">{{ errors.typeId }}</div>
          </div>

          <div class="form-group">
            <label for="departmentId">Отдел (ID) *</label>
            <input
              id="departmentId"
              v-model.number="formData.departmentId"
              type="number"
              required
              min="0"
              placeholder="Введите числовой ID отдела"
              class="form-input"
            />
            <div v-if="errors.departmentId" class="error-text">{{ errors.departmentId }}</div>
          </div>

          <div v-if="error" class="error-message">
            Ошибка при создании устройства: {{ error }}
          </div>
        </form>
      </div>

      <div class="dialog-footer">
        <button
          type="button"
          class="cancel-button"
          @click="closeDialog"
          :disabled="loading"
        >
          Отмена
        </button>
        <button
          type="button"
          class="submit-button"
          :class="{ loading: loading }"
          @click="submit"
          :disabled="loading"
        >
          <span v-if="loading">Создание...</span>
          <span v-else>Создать устройство</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { createDevice, type DeviceCreatePayload } from '../api/devices'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'created'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialog = ref(props.modelValue)
const loading = ref(false)
const error = ref<string | null>(null)

const formData = reactive<DeviceCreatePayload>({
  name: '',
  registrationNumber: '',
  deviceId: '',
  typeId: 0,
  departmentId: 0
})

const errors = reactive({
  name: '',
  registrationNumber: '',
  deviceId: '',
  typeId: '',
  departmentId: ''
})

watch(() => props.modelValue, (newVal) => {
  dialog.value = newVal
  if (newVal) {
    resetForm()
  }
})

watch(dialog, (newVal) => {
  emit('update:modelValue', newVal)
})

function resetForm() {
  formData.name = ''
  formData.registrationNumber = ''
  formData.deviceId = ''
  formData.typeId = 0
  formData.departmentId = 0
  error.value = null
  Object.keys(errors).forEach(key => {
    errors[key as keyof typeof errors] = ''
  })
}

function closeDialog() {
  dialog.value = false
}

function validateForm(): boolean {
  let isValid = true
  
  if (!formData.name.trim()) {
    errors.name = 'Название обязательно'
    isValid = false
  } else {
    errors.name = ''
  }
  
  if (!formData.registrationNumber.trim()) {
    errors.registrationNumber = 'Регистрационный номер обязателен'
    isValid = false
  } else {
    errors.registrationNumber = ''
  }
  
  if (!formData.deviceId.trim()) {
    errors.deviceId = 'ID устройства обязателен'
    isValid = false
  } else {
    errors.deviceId = ''
  }
  
  if (formData.typeId < 0 || isNaN(formData.typeId)) {
    errors.typeId = 'Тип должен быть неотрицательным числом'
    isValid = false
  } else {
    errors.typeId = ''
  }
  
  if (formData.departmentId < 0 || isNaN(formData.departmentId)) {
    errors.departmentId = 'Отдел должен быть неотрицательным числом'
    isValid = false
  } else {
    errors.departmentId = ''
  }
  
  return isValid
}

async function submit() {
  if (!validateForm()) return

  loading.value = true
  error.value = null

  try {
    await createDevice({
      name: formData.name.trim(),
      registrationNumber: formData.registrationNumber.trim(),
      deviceId: formData.deviceId.trim(),
      typeId: Number(formData.typeId),
      departmentId: Number(formData.departmentId)
    })
    
    emit('created')
    closeDialog()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Неизвестная ошибка'
    console.error('Failed to create device:', err)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
  animation: fadeIn 0.2s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.dialog-content {
  background: white;
  border-radius: 12px;
  width: 100%;
  max-width: 480px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
  overflow: hidden;
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(15px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 22px;
  border-bottom: 1px solid #e8f5e9;
  background: linear-gradient(135deg, #f1f8e9 0%, #e8f5e9 100%);
  position: relative;
}

.dialog-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, #4caf50, transparent);
}

.dialog-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 700;
  color: #1b5e20;
  letter-spacing: -0.01em;
}

.close-button {
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #c8e6c9;
  font-size: 1.3rem;
  cursor: pointer;
  color: #388e3c;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.close-button:hover {
  background: #f1f8e9;
  color: #1b5e20;
  border-color: #81c784;
  transform: scale(1.05);
  box-shadow: 0 3px 6px rgba(0, 0, 0, 0.1);
}

.dialog-body {
  padding: 22px;
  background: #fafafa;
}

.device-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-group {
  display: flex;
  flex-direction: column;
  position: relative;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-weight: 600;
  color: #2e7d32;
  font-size: 0.85rem;
  letter-spacing: 0.01em;
}

.form-group label::after {
  content: ' *';
  color: #d32f2f;
  font-weight: 700;
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  background: white;
  color: #333;
  font-family: inherit;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.05);
}

.form-input:hover {
  border-color: #a5d6a7;
}

.form-input:focus {
  outline: none;
  border-color: #4caf50;
  box-shadow: 0 0 0 3px rgba(76, 175, 80, 0.15);
  background: #f9fdf9;
}

.form-input::placeholder {
  color: #9e9e9e;
  font-weight: 400;
}

.form-input:disabled {
  background: #f5f5f5;
  color: #9e9e9e;
  cursor: not-allowed;
}

.error-text {
  color: #d32f2f;
  font-size: 0.75rem;
  margin-top: 4px;
  font-weight: 500;
  padding-left: 4px;
  animation: fadeInError 0.3s ease;
}

@keyframes fadeInError {
  from {
    opacity: 0;
    transform: translateY(-3px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.error-message {
  background: linear-gradient(135deg, #ffebee 0%, #ffcdd2 100%);
  color: #c62828;
  padding: 12px;
  border-radius: 8px;
  margin-top: 18px;
  font-size: 0.85rem;
  border-left: 3px solid #d32f2f;
  box-shadow: 0 1px 6px rgba(211, 47, 47, 0.1);
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  10%, 30%, 50%, 70%, 90% { transform: translateX(-2px); }
  20%, 40%, 60%, 80% { transform: translateX(2px); }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 18px 22px;
  border-top: 1px solid #e8f5e9;
  background: white;
  box-shadow: 0 -1px 8px rgba(0, 0, 0, 0.03);
}

.cancel-button,
.submit-button {
  padding: 10px 20px;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  min-width: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.01em;
  border: 1.5px solid transparent;
}

.cancel-button {
  background: white;
  color: #666;
  border-color: #e0e0e0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.cancel-button:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #bdbdbd;
  color: #333;
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.1);
}

.submit-button {
  background: linear-gradient(135deg, #4caf50 0%, #2e7d32 100%);
  color: white;
  border: none;
  box-shadow: 0 3px 8px rgba(76, 175, 80, 0.3);
  position: relative;
  overflow: hidden;
}

.submit-button::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.6s;
}

.submit-button:hover:not(:disabled) {
  background: linear-gradient(135deg, #66bb6a 0%, #388e3c 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.4);
}

.submit-button:hover:not(:disabled)::after {
  left: 100%;
}

.cancel-button:disabled,
.submit-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}

.submit-button:disabled::after {
  display: none;
}

/* Loading animation - only when .loading class is present */
.submit-button.loading span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.submit-button.loading span::before {
  content: '';
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  display: inline-block;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Responsive adjustments */
@media (max-width: 600px) {
  .dialog-content {
    max-width: 95%;
    border-radius: 12px;
  }
  
  .dialog-header,
  .dialog-body,
  .dialog-footer {
    padding: 20px;
  }
  
  .dialog-title {
    font-size: 1.2rem;
  }
  
  .cancel-button,
  .submit-button {
    padding: 12px 20px;
    min-width: 100px;
    font-size: 0.95rem;
  }
  
  .form-input {
    padding: 12px 14px;
  }
}

@media (max-width: 480px) {
  .dialog-footer {
    flex-direction: column;
    gap: 12px;
  }
  
  .cancel-button,
  .submit-button {
    width: 100%;
    min-width: auto;
  }
}
</style>