from pathlib import Path

path = Path("app/src/main/java/com/eveningoutpost/dexdrip/services/UiBasedCollector.java")
text = path.read_text(encoding="utf-8")

old_call = '''                        if (ICAN_RU_PACKAGE.equals(lastPackage)) {
                            sendLiveCgmToGoogleForm(
                                    timestamp,
                                    mgdl,
                                    iCanDeliveryMode
                            );
                        }
'''

new_call = '''                        sendLiveCgmToGoogleForm(
                                timestamp,
                                mgdl,
                                buildLiveCgmSourceApp()
                        );
'''

if old_call in text:
    text = text.replace(old_call, new_call, 1)
elif "buildLiveCgmSourceApp()" not in text:
    raise SystemExit("Expected iCan-only upload call not found")

old_method = '''    private void sendLiveCgmToGoogleForm(
            final long timestamp,
            final int mgdl,
            final String deliveryMode
    ) {

        final String formId = BuildConfig.ICAN_GOOGLE_FORM_ID;

        final String sourceApp =
                "xDrip-iCan-" + deliveryMode;

        if (formId == null || formId.trim().isEmpty()) {
            UserError.Log.e(TAG, "CGM live upload skipped: Google Form ID is empty");
            return;
        }
        if (formId == null || formId.trim().isEmpty()) {
            UserError.Log.e(TAG, "CGM live upload skipped: Google Form ID is empty");
            return;
        }
'''

new_method = '''    private String buildLiveCgmSourceApp() {
        if (lastPackage == null || lastPackage.trim().isEmpty()) {
            return "xDrip-UiBasedCollector";
        }

        if (ICAN_RU_PACKAGE.equals(lastPackage)) {
            return "xDrip-" + lastPackage + "-" + iCanDeliveryMode;
        }

        return "xDrip-" + lastPackage;
    }

    private void sendLiveCgmToGoogleForm(
            final long timestamp,
            final int mgdl,
            final String sourceApp
    ) {

        final String formId = BuildConfig.ICAN_GOOGLE_FORM_ID;

        if (formId == null || formId.trim().isEmpty()) {
            UserError.Log.e(TAG, "CGM live upload skipped: Google Form ID is empty");
            return;
        }
'''

if old_method in text:
    text = text.replace(old_method, new_method, 1)
elif "private String buildLiveCgmSourceApp()" not in text:
    raise SystemExit("Expected iCan-specific upload method not found")

text = text.replace(
    '        }, "iCan-CGM-FormUpload").start();',
    '        }, "CGM-FormUpload").start();',
    1,
)

path.write_text(text, encoding="utf-8")
print("Patched generic CGM live upload in", path)
