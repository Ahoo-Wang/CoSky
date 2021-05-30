package me.ahoo.cosky.core.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.lettuce.core.api.sync.RedisServerCommands;
import lombok.var;
import me.ahoo.cosky.core.CoskyException;

/**
 *
 * @author ahoo wang
 * Creation time 2021/2/27 11:45
 **/
@Deprecated
public final class RedisKeySpaces {

    public static final String KEY_SPACE_TOPIC_PREFIX_FORMAT = "__keyspace@%s__:";
    public static final String KEY_SPACE_TOPIC_FORMAT = KEY_SPACE_TOPIC_PREFIX_FORMAT + "%s";
    private final static String NOTIFY_KEYSPACE_EVENTS = "notify-keyspace-events";
    /**
     * ############################# EVENT NOTIFICATION ##############################
     * <p>
     * # Redis can notify Pub/Sub clients about events happening in the key space.
     * # This feature is documented at http://redis.io/topics/notifications
     * #
     * # For instance if keyspace events notification is enabled, and a client
     * # performs a DEL operation on key "foo" stored in the Database 0, two
     * # messages will be published via Pub/Sub:
     * #
     * # PUBLISH __keyspace@0__:foo del
     * # PUBLISH __keyevent@0__:del foo
     * #
     * # It is possible to select the events that Redis will notify among a set
     * # of classes. Every class is identified by a single character:
     * #
     * #  K     Keyspace events, published with __keyspace@<db>__ prefix.
     * #  E     Keyevent events, published with __keyevent@<db>__ prefix.
     * #  g     Generic commands (non-type specific) like DEL, EXPIRE, RENAME, ...
     * #  $     String commands
     * #  l     List commands
     * #  s     Set commands
     * #  h     Hash commands
     * #  z     Sorted set commands
     * #  x     Expired events (events generated every time a key expires)
     * #  e     Evicted events (events generated when a key is evicted for maxmemory)
     * #  t     Stream commands
     * #  m     Key-miss events (Note: It is not included in the 'A' class)
     * #  A     Alias for g$lshzxet, so that the "AKE" string means all the events
     * #        (Except key-miss events which are excluded from 'A' due to their
     * #         unique nature).
     * #
     * #  The "notify-keyspace-events" takes as argument a string that is composed
     * #  of zero or multiple characters. The empty string means that notifications
     * #  are disabled.
     * #
     * #  Example: to enable list and generic events, from the point of view of the
     * #           event name, use:
     * #
     * #  notify-keyspace-events Elg
     * #
     * #  Example 2: to get the stream of the expired keys subscribing to channel
     * #             name __keyevent@0__:expired use:
     * #
     * #  notify-keyspace-events Ex
     * #
     * #  By default all notifications are disabled because most users don't need
     * #  this feature and the feature has some overhead. Note that if you don't
     * #  specify at least one of K or E, no events will be delivered.
     * notify-keyspace-events "Kg$shx"
     */
    private final static String KEY_SPACE_TYPE = "K";
    private final static String KEY_EVENT_TYPE = "E";
    private final static String ALL_DATA_TYPE = "A";
    private final static String[] DATA_TYPES = {"g", "$", "s", "h", "x"};
    private final static int DEFAULT_DB = 0;

    public static String getTopicOfKey(String key) {
        return getTopicOfKey(DEFAULT_DB, key);
    }

    public static String getTopicOfKey(int db, String key) {
        return Strings.lenientFormat(KEY_SPACE_TOPIC_FORMAT, db, key);
    }

    public static String getKeyOfChannel(String channel) {
        return getKeyOfChannel(DEFAULT_DB, channel);
    }

    public static String getKeyOfChannel(int db, String channel) {
        var keyspaceTopicPrefix = Strings.lenientFormat(KEY_SPACE_TOPIC_PREFIX_FORMAT, db);
        return channel.substring(keyspaceTopicPrefix.length());
    }

    public static void ensureEnableKeyspace(String notifyKeySpaceEventsConfigValue) {
        if (!notifyKeySpaceEventsConfigValue.contains(KEY_SPACE_TYPE)) {
            throw new CoskyException(Strings.lenientFormat("ensureEnableKeyspace - Redis config [%s] must has value [%s].", NOTIFY_KEYSPACE_EVENTS, KEY_SPACE_TYPE));
        }
    }

    public static void ensureNotifyKeyspaceEvents(RedisServerCommands<String, String> redisServerCommands) {
        var config = redisServerCommands.configGet(NOTIFY_KEYSPACE_EVENTS);
        var configVal = config.get(NOTIFY_KEYSPACE_EVENTS);
        Preconditions.checkNotNull(configVal);
        ensureNotifyKeyspaceEvents(configVal);
    }

    public static void ensureNotifyKeyspaceEvents(String notifyKeySpaceEventsConfigValue) {
        ensureEnableKeyspace(notifyKeySpaceEventsConfigValue);
        if (notifyKeySpaceEventsConfigValue.contains(ALL_DATA_TYPE)) {
            return;
        }
        StringBuilder missConfigValue = new StringBuilder();
        for (String dataType : DATA_TYPES) {
            if (!notifyKeySpaceEventsConfigValue.contains(dataType)) {
                missConfigValue.append(dataType);
            }
        }
        if (missConfigValue.length() > 0) {
            throw new CoskyException(Strings.lenientFormat("ensureNotifyKeyspaceEvents - Redis config [%s] must has value [%s].", NOTIFY_KEYSPACE_EVENTS, missConfigValue.toString()));
        }
    }
}
