import test from 'node:test'
import assert from 'node:assert/strict'
import { asId, normalizePage, normalizePost, normalizeStatistics, normalizeUser } from '../src/api/normalizers.js'

test('BIGINT ids remain strings', () => {
  assert.equal(asId(9007199254740993n), '9007199254740993')
  assert.equal(normalizePost({ id: '9007199254740993' }).id, '9007199254740993')
})

test('normalizes common statistics aliases', () => {
  assert.deepEqual(normalizeStatistics({ totalUsers: 8, posts: 12, commentCount: 21 }), { userCount: 8, postCount: 12, commentCount: 21 })
})

test('normalizes list-shaped page data', () => {
  const page = normalizePage({ list: [{ id: 2, account: 'student' }], totalCount: 1, current: 1, size: 10 }, normalizeUser)
  assert.equal(page.items[0].id, '2')
  assert.equal(page.items[0].username, 'student')
  assert.equal(page.total, 1)
})

test('does not invent a comment count when admin post response omits it', () => {
  assert.equal(normalizePost({ id: '42', title: '图书馆自习' }).commentCount, null)
})
