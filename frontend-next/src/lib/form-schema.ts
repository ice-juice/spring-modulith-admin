import type { TypedSchema, GenericObject } from 'vee-validate'
import type { ZodType } from 'zod'
/** VeeValidate's public TypedSchema contract, using maintained Zod 4 directly. */
export function formSchema<T extends GenericObject>(schema: ZodType): TypedSchema<T, T> {
  return {
    __type: 'VVTypedSchema',
    async parse(values) {
      const result = await schema.safeParseAsync(values)
      if (result.success) return { value: result.data as T, errors: [] }
      const fields = new Map<string, string[]>()
      for (const issue of result.error.issues) {
        const path = issue.path.map(String).join('.')
        const messages = fields.get(path) || []
        messages.push(issue.message); fields.set(path, messages)
      }
      return { errors: [...fields].map(([path, errors]) => ({ path, errors })) }
    },
  }
}
