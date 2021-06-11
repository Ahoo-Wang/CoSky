redis.call('del',unpack(redis.call('keys', 'cosky:*')))

--eval "redis.call('del',unpack(redis.call('keys', '*')))" 0
