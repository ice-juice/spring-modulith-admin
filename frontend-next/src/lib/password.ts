export function validPassword(value: string) { return value.length >= 12 && new TextEncoder().encode(value).length <= 72 }
