export function parsePositiveInteger(
  value: string | null,
  fallback: number,
  maximum?: number,
): number {
  if (!value || !/^\d+$/.test(value)) {
    return fallback
  }

  const parsed = Number(value)
  if (parsed < 1 || (maximum !== undefined && parsed > maximum)) {
    return fallback
  }

  return parsed
}
