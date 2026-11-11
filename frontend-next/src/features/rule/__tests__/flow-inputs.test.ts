import { describe, expect, it } from "vitest";
import { defineComponent, h, ref } from "vue";
import { mount } from "@vue/test-utils";
import { i18n } from "@/locales";
import ExactNumberInput from "../components/ExactNumberInput.vue";
import IdentifierInput from "../components/IdentifierInput.vue";
import { printExact } from "../exact";
describe("flow form editing buffers", () => {
  it("keeps a decimal separator during editing and preserves every digit on commit", async () => {
    const value = ref<unknown>(null);
    const wrapper = mount(
      defineComponent(
        () => () =>
          h(ExactNumberInput, {
            value: value.value,
            label: "minimum",
            onUpdate: (next) => {
              value.value = next;
            },
          }),
      ),
      { global: { plugins: [i18n] } },
    );
    const input = wrapper.get("input");
    await input.setValue("0");
    await input.setValue("0.");
    expect(input.element.value).toBe("0.");
    await input.setValue("0.123456789012345678");
    expect(printExact(value.value)).toBe("0.123456789012345678");
    await input.setValue("");
    expect(value.value).toBeNull();
    wrapper.unmount();
  });
  it("keeps a name buffer through unrelated parent renders and reverts a rejected duplicate", async () => {
    const label = ref("branch key");
    const wrapper = mount(
      defineComponent(
        () => () => h(IdentifierInput, { value: "gold", label: label.value }),
      ),
    );
    const input = wrapper.get("input");
    input.element.value = "silver";
    await input.trigger("input");
    label.value = "renamed label";
    await wrapper.vm.$nextTick();
    expect(input.element.value).toBe("silver");
    await input.trigger("change");
    await wrapper.vm.$nextTick();
    expect(input.element.value).toBe("gold");
    wrapper.unmount();
  });
});
