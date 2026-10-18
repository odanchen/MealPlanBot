import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { ApiError } from '../api/client';
import { useLiveShoppingList } from './useLiveShoppingList';
beforeEach(() => { vi.useFakeTimers(); Object.defineProperty(document, 'hidden', { configurable: true, value: false }); Object.defineProperty(navigator, 'onLine', { configurable: true, value: true }); });
afterEach(() => { cleanup(); vi.useRealTimers(); });
test('slow refreshes never overlap and concurrent triggers coalesce into one trailing read', async () => {
 let resolveFirst!: (value: string) => void;
 const load = vi.fn<(_: AbortSignal) => Promise<string>>().mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve; })).mockResolvedValue('fresh');
 const hook = renderHook(() => useLiveShoppingList(load));
 await act(async () => { window.dispatchEvent(new Event('focus')); window.dispatchEvent(new Event('focus')); await vi.advanceTimersByTimeAsync(6000); });
 expect(load).toHaveBeenCalledTimes(1);
 await act(async () => { resolveFirst('initial'); });
 expect(load).toHaveBeenCalledTimes(2); expect(hook.result.current.data).toBe('fresh');
 await act(async () => { await vi.advanceTimersByTimeAsync(2000); }); expect(load).toHaveBeenCalledTimes(3);
 hook.unmount(); await act(async () => { await vi.advanceTimersByTimeAsync(6000); }); expect(load).toHaveBeenCalledTimes(3);
});
test('lost access clears private data and permanently stops refreshes for that view', async () => {
 const load = vi.fn<(_: AbortSignal) => Promise<string>>().mockResolvedValueOnce('private').mockRejectedValue(new ApiError(404, { code: 'not_found', message: 'Resource not found' }));
 const hook = renderHook(() => useLiveShoppingList(load)); await act(async () => {}); expect(hook.result.current.data).toBe('private');
 await act(async () => { await vi.advanceTimersByTimeAsync(2000); }); expect(hook.result.current.data).toBeNull(); expect(hook.result.current.inaccessible).toBe(true);
 await act(async () => { window.dispatchEvent(new Event('focus')); await vi.advanceTimersByTimeAsync(6000); await hook.result.current.refresh(); }); expect(load).toHaveBeenCalledTimes(2);
});
