// happy-dom implements HTMLCanvasElement but not a 2d rendering context
// (getContext('2d') returns null), which VehicleMap.vue relies on to
// pre-render vehicle marker icons. Stub just enough of the CanvasRenderingContext2D
// surface for that icon-drawing code to run without throwing.
class MockCanvasRenderingContext2D {
  fillStyle = ''
  strokeStyle = ''
  shadowColor = ''
  shadowBlur = 0
  shadowOffsetY = 0
  lineWidth = 1

  beginPath() {}
  closePath() {}
  moveTo() {}
  lineTo() {}
  arc() {}
  fill() {}
  stroke() {}
  save() {}
  restore() {}
  translate() {}
  scale() {}
  getImageData(_sx: number, _sy: number, sw: number, sh: number): ImageData {
    return {
      data: new Uint8ClampedArray(sw * sh * 4),
      width: sw,
      height: sh,
      colorSpace: 'srgb',
    } as ImageData
  }
}

HTMLCanvasElement.prototype.getContext = function getContext(contextId: string) {
  if (contextId === '2d') return new MockCanvasRenderingContext2D() as unknown as CanvasRenderingContext2D
  return null
} as typeof HTMLCanvasElement.prototype.getContext
