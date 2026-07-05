import { http } from './http'

export function recognizeWechatId(imageBase64, imageUrl = '') {
  return http.post('/ocr/wechat-id', { imageBase64, imageUrl }, { timeout: 35000 })
}
