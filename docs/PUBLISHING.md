# pKeyboard publishing

Source review: 2026-11-16. Files relocated into this project on 2026-11-17. This is documentation, not a signed or uploaded release.

## Files

- `store/en-US/`: Play listing title, short description and full description.
- `store/review.md`: release blockers and source evidence.
- `store/data-safety.md`: disclosure worksheet requiring release review.
- `store/assets.md`: required image formats.
- `store/release-notes-draft.txt`: replace with the actual release changes.
- [Markdown product page](site/index.md).
- [Privacy policy](../privacy.md).
- [Source inventory](publication-inventory.json).

## Markdown site

Copy the contents of `docs/site/` to the app’s directory on your Markdown site. Copy the canonical privacy policy to `privacy.md` beside the product page on the server and change the product page’s privacy link to `privacy`. The checked-in link points to the canonical project file so there is only one policy to maintain. Replace availability text only with verified download links. The website domain and production links remain owner decisions. Do not upload internal review documents, source inventories or privacy drafts.

## Existing assets

- `app/src/main/ic_launcher-playstore.png`

Retain originals at these paths. Confirm dimensions and format against `store/assets.md`; launcher XML and a PNG alone do not prove store compliance. Screenshots must show the tested release.

## Release review

Resolve the app-specific review items, verify actual network and SDK behavior, complete Data safety and age/content declarations, and build and test the chosen signed release AAB. No binary was built during documentation preparation.

## Validate publication files

```bash
python3 /sdcard/git/vkeyboard/docs/validate-publication.py
```

Official references: [app setup](https://support.google.com/googleplay/android-developer/answer/9859152), [assets](https://support.google.com/googleplay/android-developer/answer/9866151), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [app review](https://support.google.com/googleplay/android-developer/answer/9859455), [target API](https://support.google.com/googleplay/android-developer/answer/11926878).
