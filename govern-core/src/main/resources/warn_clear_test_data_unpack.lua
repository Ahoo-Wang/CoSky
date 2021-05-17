redis.call('del',unpack(redis.call('keys', 'govern:*')))

--eval "redis.call('del',unpack(redis.call('keys', '*')))" 0
