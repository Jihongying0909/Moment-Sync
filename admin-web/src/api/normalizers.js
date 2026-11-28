function firstDefined(source, keys, fallback = undefined) {
  for (const key of keys) {
    if (source?.[key] !== undefined && source?.[key] !== null) return source[key]
  }
  return fallback
}

export function asId(value) {
  return value === undefined || value === null ? '' : String(value)
}

export function normalizeStatistics(data = {}) {
  return {
    userCount: Number(firstDefined(data, ['userCount', 'users', 'totalUsers'], 0)),
    postCount: Number(firstDefined(data, ['postCount', 'posts', 'totalPosts'], 0)),
    commentCount: Number(firstDefined(data, ['commentCount', 'comments', 'totalComments'], 0)),
  }
}

export function normalizeUser(user = {}) {
  return {
    ...user,
    id: asId(user.id),
    username: firstDefined(user, ['username', 'account'], ''),
    nickname: firstDefined(user, ['nickname', 'displayName'], ''),
    role: firstDefined(user, ['role'], 'USER'),
    status: firstDefined(user, ['status'], 'ACTIVE'),
    createdAt: firstDefined(user, ['createdAt', 'created_at'], ''),
  }
}

export function normalizePost(post = {}) {
  const author = post.author || {}
  return {
    ...post,
    id: asId(post.id),
    title: firstDefined(post, ['title'], ''),
    content: firstDefined(post, ['content', 'body'], ''),
    status: firstDefined(post, ['status'], 'PUBLISHED'),
    visibility: firstDefined(post, ['visibility'], 'PUBLIC'),
    createdAt: firstDefined(post, ['createdAt', 'created_at'], ''),
    author: {
      ...author,
      id: asId(firstDefined(author, ['id'], post.userId)),
      nickname: firstDefined(author, ['nickname', 'displayName'], post.nickname || ''),
    },
    imageUrl: firstDefined(post, ['imageUrl', 'image_url'], ''),
    commentCount: firstDefined(post, ['commentCount', 'comment_count'], null),
  }
}

export function normalizePage(data = {}, mapItem = (item) => item) {
  const rawItems = firstDefined(data, ['items', 'content', 'records', 'list'], [])
  return {
    items: Array.isArray(rawItems) ? rawItems.map(mapItem) : [],
    total: Number(firstDefined(data, ['total', 'totalElements', 'totalCount'], 0)),
    page: Number(firstDefined(data, ['page', 'number', 'current'], 1)),
    pageSize: Number(firstDefined(data, ['pageSize', 'size'], 10)),
  }
}
