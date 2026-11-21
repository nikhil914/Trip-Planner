import { Suspense } from "react";
import { connection } from "next/server";
import { UploadForm } from "@/components/attachments/UploadForm";
import { resolveUploadLink } from "@/server/lib/attachments";

export const metadata = { title: "Upload tickets · Trip Planner", robots: { index: false, follow: false } };

const Shell = ({ children }: { children: React.ReactNode }) => (
  <main className="flex min-h-dvh items-center justify-center bg-black px-4 text-neutral-200">
    <div className="w-[400px] max-w-full space-y-3 rounded-lg border border-neutral-800 bg-neutral-950 p-5">{children}</div>
  </main>
);

async function Target({ params }: { params: Promise<{ token: string }> }) {
  await connection();
  const { token } = await params;
  const target = await resolveUploadLink(token);
  if (!target) {
    return (
      <Shell>
        <h1 className="text-base font-semibold text-neutral-100">This upload link has expired</h1>
        <p className="text-sm text-neutral-400">Links work for 1 hour. Ask for a new one, or attach the file from the block on the board.</p>
      </Shell>
    );
  }
  return (
    <Shell>
      <UploadForm
        token={token}
        blockTitle={target.blockTitle}
        expiresAt={target.expiresAt}
        initialFiles={target.files.map((f) => ({ id: f.id, fileName: f.fileName, sizeBytes: f.sizeBytes }))}
      />
    </Shell>
  );
}

export default function UploadPage({ params }: PageProps<"/upload/[token]">) {
  return (
    <Suspense fallback={<Shell><p className="text-sm text-neutral-500">Loading…</p></Shell>}>
      <Target params={params} />
    </Suspense>
  );
}
