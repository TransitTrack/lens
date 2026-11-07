import { describe, expect, it } from 'vitest'
import { gzipSync, strToU8 } from 'fflate'
import { decodeGeoJson } from '../../app/utils/geojson'
import { geometryLine } from '../../app/utils/gtfs'

const encode = (value: unknown) => btoa(String.fromCharCode(...gzipSync(strToU8(JSON.stringify(value)))))

describe('decodeGeoJson', () => {
  it('round-trips base64(gzip(json))', () => {
    const geo = { type: 'Point', coordinates: [17, 51.1] }
    expect(decodeGeoJson(encode(geo))).toEqual(geo)
  })
})

describe('geometryLine', () => {
  it('returns the LineString coordinates, dropping consecutive duplicates', () => {
    const encoded = encode({ type: 'LineString', coordinates: [[17, 51], [17, 51], [18, 52]] })
    expect(geometryLine(encoded)).toEqual([[17, 51], [18, 52]])
  })

  it('returns [] for missing or undecodable input', () => {
    expect(geometryLine(null)).toEqual([])
    expect(geometryLine('not-valid')).toEqual([])
  })
})
