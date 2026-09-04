# Beyond Words

**Exact Image → Words → Image**, designed to work offline.

## What this version does

- Pick an image from Android storage.
- Convert its exact bytes into a self-contained sequence of deterministic Beyond data-words.
- Copy/paste or save the words as text.
- Paste the words back into the app.
- Recover the original image with its original filename/MIME type.
- Verify length, CRC32 and SHA-256 before declaring success.
- No network permission and no server are required for encoding/decoding.

The words are **data tokens**, not an AI description of the image. They contain the information needed for exact reconstruction.

## APK

Every push to `main` and every manual workflow run builds a debug APK using GitHub Actions and uploads it as the `BeyondWords-debug-apk` artifact.

Repository: https://github.com/akggautamasar/Wordsbeyond

## Important limitation

This is a lossless representation. Already-compressed/noisy images may require many words; there is no universal lossless method that can make arbitrary high-entropy image bytes into a tiny natural-language sentence.

## Next engineering targets

1. Larger, carefully designed human-friendly dictionary.
2. Better image-specific lossless preprocessing and compression tournament.
3. Streaming/chunked encoding for very large images.
4. Optional export/import of `.beyondwords` files.
5. Stronger block-level error correction for copied/transcribed word sequences.
