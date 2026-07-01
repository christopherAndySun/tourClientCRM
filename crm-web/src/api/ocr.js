import { http } from './http'

export function recognizeWechatId(imageBase64) {
  return http.post('/ocr/wechat-id', { imageBase64 }, { timeout: 60000 })
}
