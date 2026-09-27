from pathlib import Path

path = Path("app/src/main/java/com/eveningoutpost/dexdrip/services/UiBasedCollector.java")
text = path.read_text(encoding="utf-8")

old = '''    private void processNotification(final Notification notification) {
        if (notification == null) {
            UserError.Log.e(TAG, "Null notification");
            return;
        }
        JoH.dumpBundle(notification.extras, TAG);
        if (notification.contentView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val cid = notification.getChannelId();
                UserError.Log.d(TAG, "Channel ID: " + cid);
            }
            processRemote(notification.contentView);
        } else {
            int mgdl;
            String t;
            if (notification.extras != null
                    && (isValidString(t = notification.extras.getString(Notification.EXTRA_TITLE)))
                    && (mgdl = tryExtractString(t)) > 0) {
                handleNewValue(mgdl);
            } else {
                UserError.Log.e(TAG, "Content is empty");
            }
        }
    }
'''

new = '''    private void processNotification(final Notification notification) {
        if (notification == null) {
            UserError.Log.e(TAG, "Null notification");
            return;
        }
        JoH.dumpBundle(notification.extras, TAG);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val cid = notification.getChannelId();
            UserError.Log.d(TAG, "Channel ID: " + cid);
        }

        boolean handled = false;

        if (notification.contentView != null) {
            handled = processRemote(notification.contentView);
        }

        // Some apps (including current iCan Health builds) put the glucose value only
        // in the expanded notification layout. Try that before falling back to extras.
        if (!handled && notification.bigContentView != null
                && notification.bigContentView != notification.contentView) {
            UserError.Log.d(TAG, "Trying bigContentView");
            handled = processRemote(notification.bigContentView);
        }

        // Modern/custom notifications may render without ordinary TextViews in RemoteViews,
        // while still exposing useful values through the standard notification extras.
        if (!handled) {
            handled = processNotificationExtras(notification);
        }

        if (!handled) {
            UserError.Log.e(TAG, "Content is empty or no glucose value was found");
        }
    }

    private boolean processNotificationExtras(final Notification notification) {
        if (notification.extras == null) return false;

        final String[] keys = new String[] {
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_INFO_TEXT
        };

        for (final String key : keys) {
            try {
                final CharSequence value = notification.extras.getCharSequence(key);
                if (value != null) {
                    final String text = value.toString();
                    UserError.Log.d(TAG, "Examining notification extra " + key + ": >" + text + "<");
                    final int mgdl = tryExtractNotificationString(text);
                    if (mgdl > 0) {
                        handleNewValue(mgdl);
                        return true;
                    }
                }
            } catch (Exception e) {
                UserError.Log.d(TAG, "Exception examining notification extra " + key + ": " + e);
            }
        }

        try {
            final CharSequence[] lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                for (final CharSequence line : lines) {
                    if (line == null) continue;
                    final int mgdl = tryExtractNotificationString(line.toString());
                    if (mgdl > 0) {
                        handleNewValue(mgdl);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            UserError.Log.d(TAG, "Exception examining notification text lines: " + e);
        }

        return false;
    }

    private int tryExtractNotificationString(final String text) {
        if (!isValidString(text)) return -1;

        int mgdl = tryExtractString(text);
        if (mgdl > 0) return mgdl;

        try {
            final Matcher mmol = Pattern.compile(
                    "(?i)([0-9]{1,2}[\\\\.,][0-9]+)\\\\s*mmol\\\\s*/\\\\s*l").matcher(text);
            if (mmol.find()) {
                final double value = JoH.tolerantParseDouble(mmol.group(1), -1);
                if (value > 0) {
                    return (int) Math.round(Unitized.mgdlConvert(value));
                }
            }

            final Matcher mg = Pattern.compile(
                    "(?i)([0-9]{2,3})\\\\s*mg\\\\s*/\\\\s*dl").matcher(text);
            if (mg.find()) {
                return Integer.parseInt(mg.group(1));
            }
        } catch (Exception e) {
            UserError.Log.d(TAG, "Got exception in tryExtractNotificationString: " + e);
        }

        return -1;
    }
'''

if old not in text:
    raise SystemExit("Expected processNotification block not found; upstream file changed")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Patched", path)
