export function validPassword(value: string) { return /^[A-Za-z0-9]{8,12}$/.test(value) && /[A-Za-z]/.test(value) && /[0-9]/.test(value) && !/[\r\n]/.test(value) }
export function validSecurityPassword(value: string) { return /^[A-Za-z0-9]{6,8}$/.test(value) && /[A-Za-z]/.test(value) && /[0-9]/.test(value) && !/[\r\n]/.test(value) }
