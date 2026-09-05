"use client";

import React, { useState, useRef } from "react";

interface DatasetUploadProps {
  onFileSelected: (file: File) => void;
  onLoadSample: () => void;
  isLoading?: boolean;
}

export function DatasetUpload({
  onFileSelected,
  onLoadSample,
  isLoading = false,
}: DatasetUploadProps) {
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      onFileSelected(e.dataTransfer.files[0]);
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      onFileSelected(e.target.files[0]);
    }
  };

  return (
    <div className="w-full">
      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
        className={`border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-all duration-150 ${
          isDragging
            ? "border-blue-500 bg-blue-50/60 dark:bg-blue-950/20"
            : "border-zinc-300 dark:border-zinc-700 hover:border-zinc-400 dark:hover:border-zinc-600 bg-white dark:bg-zinc-900/40"
        }`}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".csv,.xlsx,.xls"
          onChange={handleInputChange}
          className="hidden"
          disabled={isLoading}
        />

        <div className="flex flex-col items-center justify-center space-y-3">
          <div className="w-12 h-12 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center text-blue-600 dark:text-blue-400">
            <svg
              className="w-6 h-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
              />
            </svg>
          </div>

          <div>
            <p className="text-base font-medium text-zinc-800 dark:text-zinc-200">
              Drop your CSV or XLSX file here, or{" "}
              <span className="text-blue-600 dark:text-blue-400 underline">
                browse
              </span>
            </p>
            <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-1">
              Supports CSV and Excel datasets (recommended: 5–50 records)
            </p>
          </div>
        </div>
      </div>

      <div className="mt-4 flex items-center justify-between text-xs text-zinc-500 dark:text-zinc-400 px-1">
        <span>Want to test immediately?</span>
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onLoadSample();
          }}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/40 hover:bg-blue-100 dark:hover:bg-blue-900/50 rounded-md transition-colors"
        >
          <svg
            className="w-3.5 h-3.5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M13 10V3L4 14h7v7l9-11h-7z"
            />
          </svg>
          Load 5-Record Sample Dataset
        </button>
      </div>
    </div>
  );
}
