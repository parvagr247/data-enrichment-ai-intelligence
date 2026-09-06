'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';

export default function AccountPage() {
  const { user, loading, logout } = useAuth();
  const router = useRouter();
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!loading && !user) {
      router.push('/login');
    }
  }, [loading, user, router]);

  if (loading || !user) {
    return (
      <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 flex items-center justify-center">
        <div className="flex items-center gap-3 text-zinc-500">
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
          </svg>
          <span className="text-sm">Loading user account...</span>
        </div>
      </div>
    );
  }

  const copyUserId = () => {
    if (user?.id) {
      navigator.clipboard.writeText(user.id);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const getInitials = (name: string) => {
    if (!name) return 'U';
    const parts = name.trim().split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  };

  const formattedDate = user.createdAt
    ? new Date(user.createdAt).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : 'Unknown';

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 p-4 md:p-8">
      <div className="max-w-3xl mx-auto space-y-6">
        {/* Navigation bar */}
        <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
          <Link
            href="/"
            className="inline-flex items-center gap-2 text-sm text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            Back to Platform
          </Link>
          <button
            onClick={logout}
            className="px-3.5 py-1.5 rounded-lg text-xs font-medium text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950/40 hover:bg-red-100 dark:hover:bg-red-900/60 border border-red-200 dark:border-red-900/50 transition-colors flex items-center gap-1.5"
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
            Sign Out
          </button>
        </div>

        {/* Profile Card */}
        <div className="bg-white dark:bg-zinc-900 rounded-xl border border-zinc-200 dark:border-zinc-800 p-6 md:p-8 shadow-sm">
          <div className="flex flex-col sm:flex-row sm:items-center gap-5 pb-6 border-b border-zinc-200 dark:border-zinc-800">
            <div className="w-16 h-16 rounded-full bg-indigo-600 text-white flex items-center justify-center font-bold text-2xl shadow-lg shadow-indigo-500/20">
              {getInitials(user.name)}
            </div>
            <div>
              <h1 className="text-2xl font-bold text-zinc-900 dark:text-zinc-100">{user.name}</h1>
              <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-0.5">{user.email}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-6">
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-zinc-400 dark:text-zinc-500">
                User ID
              </span>
              <div className="mt-1 flex items-center gap-2">
                <code className="text-xs bg-zinc-100 dark:bg-zinc-800 px-2.5 py-1.5 rounded text-zinc-800 dark:text-zinc-200 font-mono select-all break-all">
                  {user.id}
                </code>
                <button
                  type="button"
                  onClick={copyUserId}
                  className="px-2.5 py-1.5 text-xs font-medium text-zinc-700 dark:text-zinc-300 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 rounded transition-colors"
                >
                  {copied ? 'Copied!' : 'Copy'}
                </button>
              </div>
              <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-1.5">
                All dataset jobs and entity extractions are isolated and owned by this ID.
              </p>
            </div>

            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-zinc-400 dark:text-zinc-500">
                Account Status
              </span>
              <div className="mt-1 flex items-center gap-2">
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                  Active &amp; Authenticated
                </span>
              </div>
              <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-1.5">
                Member since {formattedDate}
              </p>
            </div>
          </div>

          {/* Security & Access Summary */}
          <div className="mt-8 pt-6 border-t border-zinc-200 dark:border-zinc-800">
            <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 mb-2">
              Security &amp; Data Ownership
            </h3>
            <ul className="text-xs text-zinc-600 dark:text-zinc-400 space-y-1.5 list-disc list-inside">
              <li>Sessions are secured with HMAC-SHA256 Bearer JWT tokens.</li>
              <li>Requests through the API Gateway automatically inject validated identity headers.</li>
              <li>Batch dataset jobs and enriched entity rows are isolated to your account.</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
