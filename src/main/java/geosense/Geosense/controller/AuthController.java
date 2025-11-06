package geosense.Geosense.controller;

import geosense.Geosense.dto.UsuarioDTO;
import geosense.Geosense.dto.UsuarioEditDTO;
import geosense.Geosense.entity.Usuario;
import geosense.Geosense.repository.UsuarioRepository;
import geosense.Geosense.service.UsuarioService;
import geosense.Geosense.service.ValidacaoOracleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
public class AuthController {

	private final UsuarioService usuarioService;
	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final ValidacaoOracleService validacaoOracleService;

	public AuthController(UsuarioService usuarioService, 
	                     UsuarioRepository usuarioRepository,
	                     PasswordEncoder passwordEncoder,
	                     ValidacaoOracleService validacaoOracleService) {
		this.usuarioService = usuarioService;
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.validacaoOracleService = validacaoOracleService;
	}

	@GetMapping("/register")
	public String registerForm(Model model) {
		model.addAttribute("usuario", new UsuarioDTO("", "", ""));
		return "register";
	}

	@PostMapping("/register")
	public String registerSubmit(@Valid UsuarioDTO usuario,
	                            BindingResult bindingResult,
	                            Model model,
	                            RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("usuario", usuario);
			
			System.out.println("=== AUTH CONTROLLER - ERROS DE VALIDAÇÃO ===");
			System.out.println("Total de erros: " + bindingResult.getErrorCount());
			
			StringBuilder erros = new StringBuilder();
			bindingResult.getAllErrors().forEach(error -> {
				System.out.println("Erro: " + error.getDefaultMessage());
				System.out.println("  Campo: " + (error instanceof org.springframework.validation.FieldError 
					? ((org.springframework.validation.FieldError) error).getField() : "global"));
				if (erros.length() > 0) {
					erros.append(" ");
				}
				String mensagem = error.getDefaultMessage();
				if (!erros.toString().contains(mensagem)) {
					erros.append(mensagem);
				}
			});
			
			System.out.println("Mensagem final de erro: " + erros.toString());
			System.out.println("=============================================");
			
			if (erros.length() > 0) {
				model.addAttribute("error", erros.toString());
			}
			return "register";
		}
		try {
			usuarioService.register(usuario);
			redirectAttributes.addFlashAttribute("success", "Cadastro realizado com sucesso. Faça login.");
			return "redirect:/login?success";
		} catch (IllegalArgumentException ex) {
			model.addAttribute("usuario", usuario);
			model.addAttribute("error", ex.getMessage());
			return "register";
		}
	}

	@GetMapping("/api/profile")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getCurrentUserProfile() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(401).build();
		}

		String email = authentication.getName();
		Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
		
		if (usuarioOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		Usuario usuario = usuarioOpt.get();
		Map<String, Object> profile = new HashMap<>();
		profile.put("id", usuario.getId());
		profile.put("nome", usuario.getNome());
		profile.put("email", usuario.getEmail());
		profile.put("tipo", usuario.getTipo().name());

		return ResponseEntity.ok(profile);
	}

	@PutMapping("/api/profile")
	@ResponseBody
	public ResponseEntity<?> updateCurrentUserProfile(@RequestBody UsuarioEditDTO dto) {
		Map<String, String> validationErrors = new HashMap<>();
		
		if (dto.getNome() == null || dto.getNome().trim().isEmpty()) {
			validationErrors.put("nome", "Nome é obrigatório");
		} else if (dto.getNome().length() > 100) {
			validationErrors.put("nome", "Nome deve ter até 100 caracteres");
		}
		
		if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
			validationErrors.put("email", "Email é obrigatório");
		} else if (!dto.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
			validationErrors.put("email", "Email inválido");
		}
		
		if (dto.getSenha() != null && !dto.getSenha().trim().isEmpty()) {
			if (dto.getSenha().length() < 6) {
				validationErrors.put("senha", "Senha deve ter no mínimo 6 caracteres");
			} else if (dto.getSenha().length() > 128) {
				validationErrors.put("senha", "Senha deve ter no máximo 128 caracteres");
			}
		}
		
		if (!validationErrors.isEmpty()) {
			return ResponseEntity.badRequest().body(validationErrors);
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(401).build();
		}

		String email = authentication.getName();
		Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
		
		if (usuarioOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		Usuario usuario = usuarioOpt.get();
		
		if (dto.getSenha() != null && !dto.getSenha().trim().isEmpty()) {
			String tipoUsuario = usuario.getTipo().name();
			ValidacaoOracleService.ResultadoValidacao resultado = 
				validacaoOracleService.validarSenhaELimites(
					dto.getSenha(), 
					dto.getEmail(), 
					tipoUsuario, 
					"VALIDACAO"
				);

			if (!resultado.isValid()) {
				Map<String, String> error = new HashMap<>();
				error.put("error", resultado.getErros() != null && !resultado.getErros().isEmpty() 
					? resultado.getErros() 
					: resultado.getMensagem());
				return ResponseEntity.badRequest().body(error);
			}
		}

		if (!usuario.getEmail().equals(dto.getEmail())) {
			if (usuarioRepository.existsByEmail(dto.getEmail())) {
				Map<String, String> error = new HashMap<>();
				error.put("error", "Este email já está em uso por outro usuário");
				return ResponseEntity.badRequest().body(error);
			}
		}

		usuario.setNome(dto.getNome());
		usuario.setEmail(dto.getEmail());
		if (dto.getSenha() != null && !dto.getSenha().trim().isEmpty()) {
			usuario.setSenha(passwordEncoder.encode(dto.getSenha()));
		}
		usuarioRepository.save(usuario);

		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("message", "Perfil atualizado com sucesso");
		response.put("usuario", Map.of(
			"id", usuario.getId(),
			"nome", usuario.getNome(),
			"email", usuario.getEmail(),
			"tipo", usuario.getTipo().name()
		));

		return ResponseEntity.ok(response);
	}
}


