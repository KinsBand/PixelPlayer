package com.theveloper.pixelplay.data.account

/**
 * Where the PixelPlay account lives: a Supabase project (email/password auth + private storage).
 *
 * Fill [DEFAULT_URL] / [DEFAULT_ANON_KEY] from Supabase → Project Settings → API, or leave them
 * blank and enter them in Settings → Profile → Server (saved on the device).
 *
 * One-time setup in the Supabase SQL editor (private bucket; each user only sees their folder):
 *
 * ```sql
 * insert into storage.buckets (id, name, public) values ('pixelplay', 'pixelplay', false)
 *   on conflict (id) do nothing;
 *
 * create policy "pixelplay: read own"   on storage.objects for select to authenticated
 *   using (bucket_id = 'pixelplay' and (storage.foldername(name))[1] = auth.uid()::text);
 * create policy "pixelplay: insert own" on storage.objects for insert to authenticated
 *   with check (bucket_id = 'pixelplay' and (storage.foldername(name))[1] = auth.uid()::text);
 * create policy "pixelplay: update own" on storage.objects for update to authenticated
 *   using (bucket_id = 'pixelplay' and (storage.foldername(name))[1] = auth.uid()::text);
 * create policy "pixelplay: delete own" on storage.objects for delete to authenticated
 *   using (bucket_id = 'pixelplay' and (storage.foldername(name))[1] = auth.uid()::text);
 * ```
 *
 * If "Confirm email" is on (Authentication → Providers → Email), new accounts must open the
 * confirmation mail before the first sign-in.
 */
object CloudAccountConfig {
    /** e.g. "https://abcdefghijklmnop.supabase.co" */
    const val DEFAULT_URL = ""
    /** The project's public "anon" key (safe to ship; access is enforced by the policies above). */
    const val DEFAULT_ANON_KEY = ""

    const val BUCKET = "pixelplay"

    /** The one-time storage setup, as shown in the doc above; Profile → Advanced can copy it. */
    val SETUP_SQL: String = """
        insert into storage.buckets (id, name, public) values ('pixelplay', 'pixelplay', false)
          on conflict (id) do nothing;

        drop policy if exists "pixelplay: read own" on storage.objects;
        drop policy if exists "pixelplay: insert own" on storage.objects;
        drop policy if exists "pixelplay: update own" on storage.objects;
        drop policy if exists "pixelplay: delete own" on storage.objects;

        create policy "pixelplay: read own"   on storage.objects for select to authenticated
          using (bucket_id = 'pixelplay' and (storage.foldername(name))[1] = auth.uid()::text);
        create policy "pixelplay: insert own" on storage.objects for insert to authenticated
          with check (bucket_id = 'pixelplay' and (storage.foldername(name))[1] = auth.uid()::text);
        create policy "pixelplay: update own" on storage.objects for update to authenticated
          using (bucket_id = 'pixelplay' and (storage.foldername(name))[1] = auth.uid()::text);
        create policy "pixelplay: delete own" on storage.objects for delete to authenticated
          using (bucket_id = 'pixelplay' and (storage.foldername(name))[1] = auth.uid()::text);
    """.trimIndent()
    /** Everything for one user lives in "<user id>/<ARCHIVE_NAME>". */
    const val ARCHIVE_NAME = "pixelplay-library.zip"

    /** Auto-save cadence when enabled. */
    const val AUTO_SAVE_INTERVAL_HOURS = 12L
}
