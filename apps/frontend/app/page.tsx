import { backendConfig } from "@/config/backend";

export default function Home() {
  const services = [
    {
      name: "Research Service",
      port: 9741,
      url: backendConfig.researchServiceUrl,
      role: "Orchestration & Web Retrieval",
    },
    {
      name: "AI Intelligent Service",
      port: 9742,
      url: backendConfig.aiIntelligentServiceUrl,
      role: "Spring AI ChatClient & Extraction",
    },
    {
      name: "Dataset Service",
      port: 9743,
      url: backendConfig.datasetServiceUrl,
      role: "Dataset Persistence & Management",
    },
  ];

  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-900 dark:bg-zinc-950 dark:text-zinc-100 flex flex-col justify-center items-center p-6">
      <div className="max-w-2xl w-full space-y-8 bg-white dark:bg-zinc-900 p-8 rounded-xl shadow-sm border border-zinc-200 dark:border-zinc-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            Data Enrichment &amp; Research Engine
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1">
            Minimalist Spring Boot Backend + Next.js Frontend Setup
          </p>
        </div>

        <section className="space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
            Backend Services Configuration
          </h2>
          <div className="divide-y divide-zinc-100 dark:divide-zinc-800 border border-zinc-200 dark:border-zinc-800 rounded-lg overflow-hidden">
            {services.map((svc) => (
              <div
                key={svc.name}
                className="flex items-center justify-between p-4 bg-zinc-50/50 dark:bg-zinc-900/50"
              >
                <div>
                  <div className="font-medium text-sm">{svc.name}</div>
                  <div className="text-xs text-zinc-500 dark:text-zinc-400">
                    {svc.role}
                  </div>
                </div>
                <div className="text-right font-mono text-xs">
                  <span className="inline-block px-2 py-0.5 rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                    Port {svc.port}
                  </span>
                  <div className="text-zinc-400 mt-0.5">{svc.url}</div>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="text-xs text-zinc-500 dark:text-zinc-400 border-t border-zinc-200 dark:border-zinc-800 pt-4 flex justify-between items-center">
          <span>Frontend: http://localhost:3000</span>
          <span>Backend Range: 9741–9750</span>
        </section>
      </div>
    </div>
  );
}
