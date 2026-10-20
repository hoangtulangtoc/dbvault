package dev.dbvault.web.controller;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.dbvault.application.notification.ManageNotificationChannelService;
import dev.dbvault.core.exception.DuplicateNotificationChannelNameException;
import dev.dbvault.core.exception.NotificationChannelInUseException;
import dev.dbvault.core.model.ConnectionCheck;
import dev.dbvault.core.model.NotificationChannel;
import dev.dbvault.core.model.NotificationChannelType;
import dev.dbvault.web.dto.NotificationChannelForm;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationChannelController {
    private final ManageNotificationChannelService service;
    private final MessageSource messages;

    @GetMapping String list(Model model) {
        model.addAttribute("channels", service.listAll());
        return "notification/list";
    }

    @GetMapping("/new") String newForm(
            @RequestParam(defaultValue = "WEBHOOK") NotificationChannelType type, Model model) {
        model.addAttribute("form", NotificationChannelForm.blank(type));
        return form(model, null, type);
    }

    @PostMapping String create(@Valid @ModelAttribute("form") NotificationChannelForm form,
            BindingResult binding, Model model, RedirectAttributes flash) {
        if (!binding.hasErrors()) {
            try {
                NotificationChannel saved = service.create(form.toCommand());
                flash.addFlashAttribute("message", ConsoleText.of(messages, "flash.channel.created", saved.getName()));
                return "redirect:/notifications";
            } catch (DuplicateNotificationChannelNameException e) {
                binding.rejectValue("name", "notification.duplicate", e.getMessage());
            } catch (IllegalArgumentException e) {
                binding.reject("notification.invalid", e.getMessage());
            }
        }
        form.clearSecrets();
        return form(model, null, typeOf(form));
    }

    @GetMapping("/{id}/edit") String edit(@PathVariable UUID id, Model model, RedirectAttributes flash) {
        try {
            NotificationChannel channel = service.get(id);
            model.addAttribute("form", NotificationChannelForm.of(channel));
            return form(model, channel, channel.getType());
        } catch (NoSuchElementException e) {
            flash.addFlashAttribute("error", ConsoleText.of(messages, "flash.channel.gone"));
            return "redirect:/notifications";
        }
    }

    @PostMapping("/{id}") String update(@PathVariable UUID id,
            @Valid @ModelAttribute("form") NotificationChannelForm form, BindingResult binding,
            Model model, RedirectAttributes flash) {
        NotificationChannel current;
        try { current = service.get(id); }
        catch (NoSuchElementException e) { return "redirect:/notifications"; }
        if (!binding.hasErrors()) {
            try {
                service.edit(id, form.toCommand());
                flash.addFlashAttribute("message", ConsoleText.of(messages, "flash.channel.saved", form.getName()));
                return "redirect:/notifications";
            } catch (DuplicateNotificationChannelNameException e) {
                binding.rejectValue("name", "notification.duplicate", e.getMessage());
            } catch (IllegalArgumentException e) {
                binding.reject("notification.invalid", e.getMessage());
            }
        }
        form.clearSecrets();
        return form(model, current, current.getType());
    }

    @PostMapping("/{id}/test") String test(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            ConnectionCheck check = service.test(id);
            if (check.successful()) flash.addFlashAttribute("message", ConsoleText.of(messages, "flash.channel.testSent"));
            else flash.addFlashAttribute("error", ConsoleText.of(messages, "flash.channel.testFailed", check.message()));
        } catch (NoSuchElementException e) {
            flash.addFlashAttribute("error", ConsoleText.of(messages, "flash.channel.gone"));
        }
        return "redirect:/notifications";
    }

    @PostMapping("/{id}/delete") String delete(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            service.delete(id);
            flash.addFlashAttribute("message", ConsoleText.of(messages, "flash.channel.removed"));
        } catch (NotificationChannelInUseException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/notifications";
    }

    private String form(Model model, NotificationChannel channel, NotificationChannelType type) {
        if (channel != null) model.addAttribute("editing", channel);
        model.addAttribute("channelType", type);
        return "notification/form";
    }
    private static NotificationChannelType typeOf(NotificationChannelForm form) {
        return form.getType() == null ? NotificationChannelType.WEBHOOK : form.getType();
    }
}
