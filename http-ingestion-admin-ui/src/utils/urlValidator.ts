export function isValidHttpUrl(value: string): boolean {
  if (!value?.trim()) {
    return false;
  }
  try {
    const url = new URL(value.trim());
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

export function createHttpUrlValidator(message: string) {
  return (_: unknown, value: string) => {
    if (!value?.trim()) {
      return Promise.reject(new Error(message));
    }
    if (!isValidHttpUrl(value)) {
      return Promise.reject(new Error(message));
    }
    return Promise.resolve();
  };
}
