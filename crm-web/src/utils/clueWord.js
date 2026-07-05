import { downloadBlob } from './download'
import { resolveAssetUrl } from './assets'

const MAX_IMAGE_WIDTH = 460
const MAX_IMAGE_HEIGHT = 650

export async function downloadClueWordFile(clue) {
  const { AlignmentType, Document, ImageRun, Packer, Paragraph, TextRun } = await import('docx')
  const children = [
    new Paragraph({
      children: [
        new TextRun({
          text: clue.contactInfo || '待补充',
          size: 20
        })
      ]
    }),
    ...blankLines(Paragraph, 2)
  ]

  const images = [
    ...(clue.douyinImages || []),
    ...(clue.wechatImages || [])
  ].filter((image) => image?.url)

  for (const [index, image] of images.entries()) {
    const wordImage = await loadWordImage(image.url)
    children.push(new Paragraph({
      alignment: AlignmentType.LEFT,
      children: [
        new ImageRun({
          type: 'jpg',
          data: wordImage.data,
          transformation: fitImage(wordImage.width, wordImage.height),
          altText: {
            title: image.name || `客户截图${index + 1}`,
            description: image.name || `客户截图${index + 1}`,
            name: image.name || `客户截图${index + 1}`
          }
        })
      ]
    }))
    children.push(...blankLines(Paragraph, 2))
  }

  const doc = new Document({
    sections: [
      {
        properties: {},
        children
      }
    ]
  })
  const blob = await Packer.toBlob(doc)
  downloadBlob(blob, `${safeFilename(clue.customerCode || '客户线索')}.docx`)
}

function blankLines(Paragraph, count) {
  return Array.from({ length: count }, () => new Paragraph({ text: '' }))
}

async function loadWordImage(url) {
  const imageUrl = resolveAssetUrl(url)
  const image = await loadImage(imageUrl)
  const canvas = document.createElement('canvas')
  const scale = Math.min(1, 1400 / Math.max(image.naturalWidth, image.naturalHeight))
  canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
  canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
  const context = canvas.getContext('2d')
  context.drawImage(image, 0, 0, canvas.width, canvas.height)
  const blob = await canvasToBlob(canvas)
  return {
    data: new Uint8Array(await blob.arrayBuffer()),
    width: canvas.width,
    height: canvas.height
  }
}

function loadImage(url) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('图片加载失败，请确认图片文件是否存在'))
    image.src = url
  })
}

function canvasToBlob(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
        return
      }
      reject(new Error('图片写入 Word 失败'))
    }, 'image/jpeg', 0.92)
  })
}

function fitImage(width, height) {
  const scale = Math.min(MAX_IMAGE_WIDTH / width, MAX_IMAGE_HEIGHT / height, 1)
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale))
  }
}

function safeFilename(value) {
  return String(value || '客户线索').replace(/[\\/:*?"<>|]/g, '_')
}
